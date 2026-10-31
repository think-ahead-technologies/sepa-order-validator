// ABOUTME: Integration tests for the validation module together with intake, using real SFTP and PostgreSQL.
// ABOUTME: Covers validation outcomes, status updates and race-free duplicate MsgId detection per submitter.
package dev.thinkahead.sepa.validation;

import static dev.thinkahead.sepa.Fixtures.bytes;
import static dev.thinkahead.sepa.Fixtures.uniqueMessageId;
import static dev.thinkahead.sepa.Fixtures.validPain001;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.thinkahead.sepa.CapturedEvents;
import dev.thinkahead.sepa.SftpTestClient;
import dev.thinkahead.sepa.TestSupportConfiguration;
import dev.thinkahead.sepa.TestcontainersConfiguration;
import dev.thinkahead.sepa.intake.PaymentFileReceived;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.transaction.support.TransactionTemplate;

@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
class PaymentFileValidationIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    SftpTestClient customer;

    @Autowired
    CapturedEvents events;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AcceptedMessages acceptedMessages;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    TransactionTemplate transaction;

    @Test
    void redeliveredReceivedEventDoesNotValidateTheFileAgain() {
        PaymentFileValidated first = uploadAndAwaitValidation(validPain001(uniqueMessageId()));
        PaymentFileReceived received = events.ofType(PaymentFileReceived.class).stream()
                .filter(e -> e.fileId().equals(first.fileId())).findFirst().orElseThrow();

        transaction.executeWithoutResult(status -> publisher.publishEvent(received));

        await().during(Duration.ofSeconds(2)).atMost(TIMEOUT).untilAsserted(() ->
                assertThat(events.ofType(PaymentFileValidated.class))
                        .filteredOn(e -> e.fileId().equals(first.fileId()))
                        .singleElement()
                        .satisfies(e -> assertThat(e.accepted()).isTrue()));
        assertThat(statusOf(first.fileId())).isEqualTo("ACCEPTED");
    }

    @Test
    void rejectsAFileAboveTheMaximumSizeWithFf01() {
        PaymentFileValidated validated = uploadAndAwaitValidation(
                dev.thinkahead.sepa.Fixtures.paddedTo(validPain001(uniqueMessageId()), 1_100_000));

        assertThat(validated.accepted()).isFalse();
        assertThat(validated.violations())
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("FF01");
                    assertThat(v.path()).isEqualTo("Document");
                    assertThat(v.message()).contains("maximum size");
                });
        assertThat(statusOf(validated.fileId())).isEqualTo("REJECTED");
    }

    @Test
    void acceptsValidFileAndMarksItAccepted() {
        String messageId = uniqueMessageId();

        PaymentFileValidated validated = uploadAndAwaitValidation(validPain001(messageId));

        assertThat(validated.accepted()).isTrue();
        assertThat(validated.originalMessageId()).isEqualTo(messageId);
        assertThat(validated.violations()).isEmpty();
        assertThat(statusOf(validated.fileId())).isEqualTo("ACCEPTED");
    }

    @Test
    void rejectsInvalidFileAndMarksItRejected() {
        PaymentFileValidated validated = uploadAndAwaitValidation(
                validPain001(uniqueMessageId()).replace("NL91ABNA0417164300", "NL91ABNA0417164301"));

        assertThat(validated.accepted()).isFalse();
        assertThat(validated.violations()).extracting(Violation::reasonCode).containsExactly("AC03");
        assertThat(statusOf(validated.fileId())).isEqualTo("REJECTED");
    }

    @Test
    void rejectsResubmissionOfAnAcceptedMessageIdWithDu01() {
        String xml = validPain001(uniqueMessageId());
        PaymentFileValidated first = uploadAndAwaitValidation(xml);

        PaymentFileValidated resubmission = uploadAndAwaitValidation(xml);

        assertThat(first.accepted()).isTrue();
        assertThat(resubmission.accepted()).isFalse();
        assertThat(resubmission.violations())
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("DU01");
                    assertThat(v.path()).isEqualTo("GrpHdr/MsgId");
                });
        assertThat(statusOf(resubmission.fileId())).isEqualTo("REJECTED");
    }

    @Test
    void acceptsCorrectedResubmissionAfterARejection() {
        String messageId = uniqueMessageId();
        PaymentFileValidated rejected = uploadAndAwaitValidation(
                validPain001(messageId).replace("NL91ABNA0417164300", "NL91ABNA0417164301"));

        PaymentFileValidated corrected = uploadAndAwaitValidation(validPain001(messageId));

        assertThat(rejected.accepted()).isFalse();
        assertThat(corrected.accepted()).isTrue();
    }

    @Test
    void scopesMessageIdsPerSubmitter() {
        String messageId = uniqueMessageId();

        assertThat(acceptedMessages.register("sftp:customer-a", messageId, UUID.randomUUID())).isTrue();
        assertThat(acceptedMessages.register("sftp:customer-b", messageId, UUID.randomUUID())).isTrue();
        assertThat(acceptedMessages.register("sftp:customer-a", messageId, UUID.randomUUID())).isFalse();
    }

    @Test
    void registersAMessageIdExactlyOnceUnderConcurrentAcceptance() throws Exception {
        String messageId = uniqueMessageId();
        int contenders = 8;
        var start = new CountDownLatch(1);
        Callable<Boolean> register = () -> {
            start.await();
            return acceptedMessages.register("sftp:race", messageId, UUID.randomUUID());
        };

        try (var executor = Executors.newFixedThreadPool(contenders)) {
            List<Future<Boolean>> results = IntStream.range(0, contenders)
                    .mapToObj(i -> executor.submit(register)).toList();
            start.countDown();

            long winners = 0;
            for (Future<Boolean> result : results) {
                winners += result.get() ? 1 : 0;
            }
            assertThat(winners).isEqualTo(1);
        }
    }

    private PaymentFileValidated uploadAndAwaitValidation(String xml) {
        String fileName = "validation-" + UUID.randomUUID() + ".xml";
        customer.upload("inbound", fileName, bytes(xml));

        UUID fileId = await().atMost(TIMEOUT).until(
                () -> events.ofType(PaymentFileReceived.class).stream()
                        .filter(e -> e.fileName().equals(fileName)).map(PaymentFileReceived::fileId)
                        .findFirst().orElse(null),
                id -> id != null);

        return await().atMost(TIMEOUT).until(
                () -> events.ofType(PaymentFileValidated.class).stream()
                        .filter(e -> e.fileId().equals(fileId)).findFirst().orElse(null),
                e -> e != null);
    }

    private String statusOf(UUID fileId) {
        return await().atMost(TIMEOUT).until(
                () -> jdbc.sql("select status from payment_file where id = ?").param(fileId)
                        .query(String.class).single(),
                status -> !status.equals("RECEIVED"));
    }
}
