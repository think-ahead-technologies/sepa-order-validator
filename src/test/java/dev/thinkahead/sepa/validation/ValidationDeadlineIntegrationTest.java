// ABOUTME: Integration test of the VALIDATE deadline with real SFTP and PostgreSQL and a 1 ms validation timeout.
// ABOUTME: A file whose validation runs past the deadline is rejected once with FF01 and is not retried.
package dev.thinkahead.sepa.validation;

import static dev.thinkahead.sepa.Fixtures.bytes;
import static dev.thinkahead.sepa.Fixtures.uniqueMessageId;
import static dev.thinkahead.sepa.Fixtures.validPain001;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.thinkahead.sepa.CapturedEvents;
import dev.thinkahead.sepa.Fixtures;
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
import org.springframework.test.context.TestPropertySource;

@ApplicationModuleTest(mode = BootstrapMode.ALL_DEPENDENCIES)
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
@TestPropertySource(properties = "sepa.processing.validate.timeout=PT0.001S")
class ValidationDeadlineIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    SftpTestClient customer;

    @Autowired
    CapturedEvents events;

    @Autowired
    JdbcClient jdbc;

    @Test
    void aValidationPastItsDeadlineIsRejectedOnceWithFf01() {
        String fileName = "deadline-" + UUID.randomUUID() + ".xml";
        customer.upload("inbound", fileName, bytes(Fixtures.paddedTo(validPain001(uniqueMessageId()), 900_000)));

        UUID fileId = await().atMost(TIMEOUT)
                .until(
                        () -> events.ofType(PaymentFileReceived.class).stream()
                                .filter(e -> e.fileName().equals(fileName))
                                .map(PaymentFileReceived::fileId)
                                .findFirst()
                                .orElse(null),
                        id -> id != null);
        PaymentFileValidated validated = await().atMost(TIMEOUT)
                .until(
                        () -> events.ofType(PaymentFileValidated.class).stream()
                                .filter(e -> e.fileId().equals(fileId))
                                .findFirst()
                                .orElse(null),
                        e -> e != null);

        assertThat(validated.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.reasonCode()).isEqualTo("FF01");
            assertThat(violation.message()).contains("time limit");
        });
        assertThat(jdbc.sql("select state || ':' || attempts from work_item where file_id = ? and step = 'VALIDATE'")
                        .param(fileId)
                        .query(String.class)
                        .single())
                .as("completed on the first attempt")
                .isEqualTo("DONE:1");
    }
}
