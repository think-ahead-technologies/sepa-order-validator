// ABOUTME: Integration tests for the reporting module with intake, on real SFTP and PostgreSQL.
// ABOUTME: A PaymentFileValidated event produces a stored, schema-valid pain.002 delivered to SFTP outbound.
package dev.thinkahead.sepa.reporting;

import static dev.thinkahead.sepa.Fixtures.VALID_PAIN_001;
import static dev.thinkahead.sepa.Fixtures.bytes;
import static dev.thinkahead.sepa.reporting.Pain002WriterTest.assertSchemaValid;
import static dev.thinkahead.sepa.reporting.Pain002WriterTest.parse;
import static dev.thinkahead.sepa.reporting.Pain002WriterTest.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.thinkahead.sepa.CapturedEvents;
import dev.thinkahead.sepa.LogLines;
import dev.thinkahead.sepa.SftpTestClient;
import dev.thinkahead.sepa.TestSupportConfiguration;
import dev.thinkahead.sepa.TestcontainersConfiguration;
import dev.thinkahead.sepa.intake.PaymentFileReceived;
import dev.thinkahead.sepa.validation.PaymentFileValidated;
import dev.thinkahead.sepa.validation.Violation;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
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

@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class StatusReportIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    SftpTestClient customer;

    @Autowired
    CapturedEvents events;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    TransactionTemplate transaction;

    @Test
    void storesAndDeliversAcceptanceReport() throws Exception {
        String fileName = "report-accp-" + UUID.randomUUID() + ".xml";
        UUID fileId = receive(fileName);

        StatusReportDelivered delivered = validated(new PaymentFileValidated(
                fileId, "MSG-2026-10-31-001", "pain.001.001.09", true, List.of()));

        assertThat(delivered.groupStatus()).isEqualTo(GroupStatus.ACCP);
        assertThat(delivered.reportMessageId()).hasSizeLessThanOrEqualTo(35);

        byte[] stored = storedReport(fileId);
        assertSchemaValid(stored);
        assertThat(text(parse(stored), "GrpHdr/MsgId")).isEqualTo(delivered.reportMessageId());
        assertThat(text(parse(stored), "OrgnlGrpInfAndSts/OrgnlMsgId")).isEqualTo("MSG-2026-10-31-001");

        String reportName = fileName.replace(".xml", "") + "-" + fileId + ".pain002.xml";
        assertThat(customer.download("outbound", reportName)).isEqualTo(stored);
    }

    @Test
    void storesAndDeliversRejectionReportWithReasons(CapturedOutput output) throws Exception {
        UUID fileId = receive("report-rjct-" + UUID.randomUUID() + ".xml");

        StatusReportDelivered delivered = validated(new PaymentFileValidated(
                fileId, "MSG-2026-10-31-001", "pain.001.001.09", false,
                List.of(new Violation("AC03", "PmtInf[1]/CdtTrfTxInf[1]/CdtrAcct/Id/IBAN", "Invalid IBAN"))));

        assertThat(delivered.groupStatus()).isEqualTo(GroupStatus.RJCT);
        byte[] stored = storedReport(fileId);
        assertSchemaValid(stored);
        assertThat(text(parse(stored), "OrgnlGrpInfAndSts/StsRsnInf[1]/Rsn/Cd")).isEqualTo("AC03");
        assertThat(LogLines.messagesForFile(output, fileId))
                .contains("Delivered status report " + delivered.reportMessageId() + " with group status RJCT");
    }

    @Test
    void redeliveredValidationOutcomeDoesNotCreateASecondReport() {
        String fileName = "report-redelivered-" + UUID.randomUUID() + ".xml";
        UUID fileId = receive(fileName);
        var outcome = new PaymentFileValidated(fileId, "MSG-REDELIVERED", "pain.001.001.09", true, List.of());
        StatusReportDelivered first = validated(outcome);

        transaction.executeWithoutResult(status -> publisher.publishEvent(outcome));

        await().during(Duration.ofSeconds(2)).atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(jdbc.sql("select count(*) from status_report where file_id = ?").param(fileId)
                    .query(Integer.class).single()).isEqualTo(1);
            assertThat(deliveriesFor(fileId)).isEqualTo(1);
        });
        assertThat(storedReportMessageId(fileId)).isEqualTo(first.reportMessageId());
        assertThat(customer.list("outbound")).filteredOn(name -> name.startsWith(fileName.replace(".xml", "")))
                .hasSize(1);
    }

    @Test
    void aReDrivenDeliveryResendsTheStoredReportUnchanged() {
        String fileName = "report-resent-" + UUID.randomUUID() + ".xml";
        UUID fileId = receive(fileName);
        StatusReportDelivered first = validated(
                new PaymentFileValidated(fileId, "MSG-RESENT", "pain.001.001.09", true, List.of()));
        byte[] stored = storedReport(fileId);
        String reportName = fileName.replace(".xml", "") + "-" + fileId + ".pain002.xml";
        assertThat(customer.list("outbound")).contains(reportName);

        jdbc.sql("""
                        update work_item set state = 'PENDING', next_attempt_at = now(), lease_token = null,
                                             locked_until = null
                        where file_id = ? and step = 'DELIVER'""").param(fileId).update();

        await().atMost(TIMEOUT).until(() -> deliveriesFor(fileId) == 2);
        assertThat(customer.download("outbound", reportName)).isEqualTo(stored);
        assertThat(customer.list("outbound")).filteredOn(name -> name.startsWith(fileName.replace(".xml", "")))
                .hasSize(1);
        assertThat(storedReportMessageId(fileId)).isEqualTo(first.reportMessageId());
    }

    @Test
    void deliveryIsRetriedUntilTheOutboundChannelRecovers() {
        String fileName = "report-retried-" + UUID.randomUUID() + ".xml";
        UUID fileId = receive(fileName);

        customer.chmod(".", "outbound", 0555);
        try {
            transaction.executeWithoutResult(status -> publisher.publishEvent(
                    new PaymentFileValidated(fileId, "MSG-RETRIED", "pain.001.001.09", true, List.of())));

            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(jdbc.sql("""
                            select attempts from work_item where file_id = ? and step = 'DELIVER'
                            and state = 'PENDING' and last_error like 'Cannot write SFTP outbound file%'""")
                    .param(fileId).query(Integer.class).optional()).hasValueSatisfying(
                            attempts -> assertThat(attempts).isGreaterThanOrEqualTo(2)));
            assertThat(deliveriesFor(fileId)).isZero();
        } finally {
            customer.chmod(".", "outbound", 0755);
        }

        await().atMost(TIMEOUT).until(() -> deliveriesFor(fileId) == 1);
        assertThat(jdbc.sql("select state from work_item where file_id = ? and step = 'DELIVER'").param(fileId)
                .query(String.class).single()).isEqualTo("DONE");
    }

    private long deliveriesFor(UUID fileId) {
        return events.ofType(StatusReportDelivered.class).stream().filter(e -> e.fileId().equals(fileId)).count();
    }

    private String storedReportMessageId(UUID fileId) {
        return jdbc.sql("select message_id from status_report where file_id = ?").param(fileId)
                .query(String.class).single();
    }

    private UUID receive(String fileName) {
        customer.upload("inbound", fileName, bytes(VALID_PAIN_001));
        return await().atMost(TIMEOUT).until(
                () -> events.ofType(PaymentFileReceived.class).stream()
                        .filter(e -> e.fileName().equals(fileName)).map(PaymentFileReceived::fileId)
                        .findFirst().orElse(null),
                id -> id != null);
    }

    private StatusReportDelivered validated(PaymentFileValidated event) {
        transaction.executeWithoutResult(status -> publisher.publishEvent(event));
        return await().atMost(TIMEOUT).until(
                () -> events.ofType(StatusReportDelivered.class).stream()
                        .filter(e -> e.fileId().equals(event.fileId())).findFirst().orElse(null),
                e -> e != null);
    }

    private byte[] storedReport(UUID fileId) {
        return jdbc.sql("select content from status_report where file_id = ?").param(fileId)
                .query(byte[].class).single();
    }
}
