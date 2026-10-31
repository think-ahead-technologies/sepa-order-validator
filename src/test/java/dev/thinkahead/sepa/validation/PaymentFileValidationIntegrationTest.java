// ABOUTME: Integration tests for the validation module together with intake, using real SFTP and PostgreSQL.
// ABOUTME: Covers outcomes, status updates, duplicate MsgIds, re-driven and exhausted VALIDATE work items.
package dev.thinkahead.sepa.validation;

import static dev.thinkahead.sepa.Fixtures.bytes;
import static dev.thinkahead.sepa.Fixtures.uniqueMessageId;
import static dev.thinkahead.sepa.Fixtures.validPain001;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.thinkahead.sepa.CapturedEvents;
import dev.thinkahead.sepa.LogLines;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.transaction.support.TransactionTemplate;

@ApplicationModuleTest(mode = BootstrapMode.ALL_DEPENDENCIES)
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class PaymentFileValidationIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_VALIDATE_ATTEMPTS = 3;

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
    void aReDrivenValidateItemDoesNotValidateTheFileAgain() {
        PaymentFileValidated first = uploadAndAwaitValidation(validPain001(uniqueMessageId()));

        jdbc.sql("""
                        update work_item set state = 'PENDING', next_attempt_at = now(), lease_token = null,
                                             locked_until = null
                        where file_id = ? and step = 'VALIDATE'""").param(first.fileId()).update();

        await().atMost(TIMEOUT).until(() -> validateItemState(first.fileId()).equals("DONE"));
        await().during(Duration.ofSeconds(2)).atMost(TIMEOUT).untilAsserted(() ->
                assertThat(events.ofType(PaymentFileValidated.class))
                        .filteredOn(e -> e.fileId().equals(first.fileId()))
                        .singleElement()
                        .satisfies(e -> assertThat(e.accepted()).isTrue()));
        assertThat(statusOf(first.fileId())).isEqualTo("ACCEPTED");
    }

    @Test
    void answersWithATechnicalRejectionOnceAllValidationAttemptsFailed(CapturedOutput output) {
        jdbc.sql("""
                create or replace function block_message_registration() returns trigger language plpgsql as $$
                begin raise exception 'message registry unavailable'; end; $$""").update();
        jdbc.sql("""
                create trigger block_message_registration before insert on accepted_message
                for each row execute function block_message_registration()""").update();
        try {
            PaymentFileValidated validated = uploadAndAwaitValidation(validPain001(uniqueMessageId()));

            assertThat(validated.accepted()).isFalse();
            assertThat(validated.violations()).singleElement().satisfies(violation -> {
                assertThat(violation.reasonCode()).isEqualTo("FF01");
                assertThat(violation.message()).contains("technical error").contains(validated.fileId().toString());
            });
            assertThat(statusOf(validated.fileId())).isEqualTo("REJECTED");
            assertThat(validateItemState(validated.fileId())).isEqualTo("DONE");
            assertThat(validateItemAttempts(validated.fileId())).isEqualTo(MAX_VALIDATE_ATTEMPTS);
            assertThat(LogLines.messagesForFile(output, validated.fileId()))
                    .anySatisfy(message -> assertThat(message).startsWith("VALIDATE gave up after"));
        } finally {
            jdbc.sql("drop trigger block_message_registration on accepted_message").update();
        }
    }

    @Test
    void aFileThatKeptCrashingItsWorkerGetsATechnicalRejectionWithoutBeingProcessedAgain(CapturedOutput output) {
        UUID fileId = UUID.randomUUID();
        // A file whose earlier attempts all died with their worker (e.g. out of memory) and whose last lease expired.
        transaction.executeWithoutResult(status -> {
            jdbc.sql("""
                            insert into payment_file (id, channel, submitter, file_name, size_bytes, sha256, content, status)
                            values (?, 'SFTP', 'sftp:sepa', 'crash-loop.xml', 3, repeat('0', 64), ?, 'RECEIVED')""")
                    .params(fileId, "<x>".getBytes()).update();
            jdbc.sql("""
                            insert into work_item (file_id, step, attempts, lease_token, locked_until)
                            values (?, 'VALIDATE', ?, gen_random_uuid(), now() - interval '1 second')""")
                    .params(fileId, MAX_VALIDATE_ATTEMPTS).update();
        });

        PaymentFileValidated validated = await().atMost(TIMEOUT).until(
                () -> events.ofType(PaymentFileValidated.class).stream()
                        .filter(e -> e.fileId().equals(fileId)).findFirst().orElse(null),
                e -> e != null);

        assertThat(validated.violations()).extracting(Violation::reasonCode).containsExactly("FF01");
        assertThat(validated.violations().getFirst().message()).contains("technical error");
        assertThat(validateItemAttempts(fileId)).isEqualTo(MAX_VALIDATE_ATTEMPTS + 1);
        assertThat(LogLines.messagesForFile(output, fileId))
                .anySatisfy(message -> assertThat(message).startsWith("VALIDATE gave up after"));
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
    void rejectsInvalidFileAndMarksItRejected(CapturedOutput output) {
        PaymentFileValidated validated = uploadAndAwaitValidation(
                validPain001(uniqueMessageId()).replace("NL91ABNA0417164300", "NL91ABNA0417164301"));

        assertThat(validated.accepted()).isFalse();
        assertThat(validated.violations()).extracting(Violation::reasonCode).containsExactly("AC03");
        assertThat(statusOf(validated.fileId())).isEqualTo("REJECTED");
        assertThat(LogLines.messagesForFile(output, validated.fileId()))
                .contains("Validated payment file: rejected with reason codes [AC03]");
        assertThat(output).doesNotContain("NL91ABNA0417164301");
    }

    @Test
    void acceptsCreditorsInCountriesThatJoinedSepaIn2025() {
        PaymentFileValidated validated = uploadAndAwaitValidation(validPain001(uniqueMessageId())
                .replace("NL91ABNA0417164300", "AL47212110090000000235698741")
                .replace("FR1420041010050500013M02606", "RS35260005601001611379"));

        assertThat(validated.violations()).isEmpty();
        assertThat(validated.accepted()).isTrue();
    }

    @Test
    void rejectsASchemaValidLowerCaseIbanWithAc03() {
        PaymentFileValidated validated = uploadAndAwaitValidation(
                validPain001(uniqueMessageId()).replace("NL91ABNA0417164300", "NL91abna0417164300"));

        assertThat(validated.accepted()).isFalse();
        assertThat(validated.violations()).extracting(Violation::reasonCode).containsExactly("AC03");
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

    private String validateItemState(UUID fileId) {
        return jdbc.sql("select state from work_item where file_id = ? and step = 'VALIDATE'").param(fileId)
                .query(String.class).single();
    }

    private int validateItemAttempts(UUID fileId) {
        return jdbc.sql("select attempts from work_item where file_id = ? and step = 'VALIDATE'").param(fileId)
                .query(Integer.class).single();
    }

    private String statusOf(UUID fileId) {
        return await().atMost(TIMEOUT).until(
                () -> jdbc.sql("select status from payment_file where id = ?").param(fileId)
                        .query(String.class).single(),
                status -> !status.equals("RECEIVED"));
    }
}
