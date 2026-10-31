// ABOUTME: Integration tests for the validation module together with intake, using real SFTP and PostgreSQL.
// ABOUTME: A received file is validated asynchronously, its status updated and PaymentFileValidated published.
package dev.thinkahead.sepa.validation;

import static dev.thinkahead.sepa.Fixtures.VALID_PAIN_001;
import static dev.thinkahead.sepa.Fixtures.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.thinkahead.sepa.CapturedEvents;
import dev.thinkahead.sepa.SftpTestClient;
import dev.thinkahead.sepa.TestSupportConfiguration;
import dev.thinkahead.sepa.TestcontainersConfiguration;
import dev.thinkahead.sepa.intake.PaymentFileReceived;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;

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

    @Test
    void acceptsValidFileAndMarksItAccepted() {
        PaymentFileValidated validated = uploadAndAwaitValidation(VALID_PAIN_001);

        assertThat(validated.accepted()).isTrue();
        assertThat(validated.originalMessageId()).isEqualTo("MSG-2026-10-31-001");
        assertThat(validated.violations()).isEmpty();
        assertThat(statusOf(validated.fileId())).isEqualTo("ACCEPTED");
    }

    @Test
    void rejectsInvalidFileAndMarksItRejected() {
        PaymentFileValidated validated = uploadAndAwaitValidation(
                VALID_PAIN_001.replace("NL91ABNA0417164300", "NL91ABNA0417164301"));

        assertThat(validated.accepted()).isFalse();
        assertThat(validated.violations()).extracting(Violation::reasonCode).containsExactly("AC03");
        assertThat(statusOf(validated.fileId())).isEqualTo("REJECTED");
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
