// ABOUTME: Integration tests for the intake module alone against real SFTP and PostgreSQL containers.
// ABOUTME: Covers polling, storage with hash and status, archiving, event publication and report delivery.
package dev.thinkahead.sepa.intake;

import static dev.thinkahead.sepa.Fixtures.VALID_PAIN_001;
import static dev.thinkahead.sepa.Fixtures.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.thinkahead.sepa.CapturedEvents;
import dev.thinkahead.sepa.SftpTestClient;
import dev.thinkahead.sepa.TestSupportConfiguration;
import dev.thinkahead.sepa.TestcontainersConfiguration;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
class SftpIntakeIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    SftpTestClient customer;

    @Autowired
    CapturedEvents events;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PaymentFiles paymentFiles;

    @Test
    void storesUploadedFileWithHashAndPublishesEvent() throws Exception {
        String fileName = "intake-" + UUID.randomUUID() + ".xml";
        byte[] content = bytes(VALID_PAIN_001);

        customer.upload("inbound", fileName, content);

        PaymentFileReceived received = await().atMost(TIMEOUT).until(
                () -> events.ofType(PaymentFileReceived.class).stream()
                        .filter(e -> e.fileName().equals(fileName)).findFirst().orElse(null),
                e -> e != null);

        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        assertThat(received.channel()).isEqualTo(Channel.SFTP);
        assertThat(received.sha256()).isEqualTo(expectedHash);
        assertThat(jdbc.sql("select status, sha256, channel from payment_file where id = ?")
                .param(received.fileId())
                .query((rs, n) -> rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3))
                .single())
                .isEqualTo("RECEIVED|" + expectedHash + "|SFTP");
        assertThat(paymentFiles.content(received.fileId())).isEqualTo(content);
    }

    @Test
    void movesProcessedFileOutOfInboundDirectory() {
        String fileName = "archive-" + UUID.randomUUID() + ".xml";

        customer.upload("inbound", fileName, bytes(VALID_PAIN_001));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(customer.list("inbound")).doesNotContain(fileName);
            assertThat(customer.list("processed")).anySatisfy(name -> assertThat(name).endsWith(fileName));
        });
    }

    @Test
    void ignoresFilesThatAreNotXml() throws InterruptedException {
        String fileName = "upload-in-progress-" + UUID.randomUUID() + ".tmp";

        customer.upload("inbound", fileName, bytes(VALID_PAIN_001));
        Thread.sleep(2_000);

        assertThat(customer.list("inbound")).contains(fileName);
    }

    @Test
    void recordsValidationOutcome() {
        UUID fileId = receive("outcome-" + UUID.randomUUID() + ".xml");

        paymentFiles.recordValidationOutcome(fileId, false);

        assertThat(jdbc.sql("select status from payment_file where id = ?").param(fileId)
                .query(String.class).single()).isEqualTo("REJECTED");
    }

    @Test
    void deliversStatusReportThroughTheSftpChannel() {
        String fileName = "deliver-" + UUID.randomUUID() + ".xml";
        UUID fileId = receive(fileName);
        byte[] report = bytes("<Document/>");

        paymentFiles.deliverStatusReport(fileId, report);

        String reportName = fileName.replace(".xml", "") + "-" + fileId + ".pain002.xml";
        assertThat(customer.list("outbound")).contains(reportName);
        assertThat(customer.download("outbound", reportName)).isEqualTo(report);
    }

    private UUID receive(String fileName) {
        customer.upload("inbound", fileName, bytes(VALID_PAIN_001));
        return await().atMost(TIMEOUT).until(
                () -> events.ofType(PaymentFileReceived.class).stream()
                        .filter(e -> e.fileName().equals(fileName)).map(PaymentFileReceived::fileId)
                        .findFirst().orElse(null),
                id -> id != null);
    }
}
