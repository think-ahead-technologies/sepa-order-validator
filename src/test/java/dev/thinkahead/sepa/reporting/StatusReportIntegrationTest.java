// ABOUTME: Integration tests for the reporting module with its dependencies, on real SFTP and PostgreSQL.
// ABOUTME: A validated file gets exactly one stored, schema-valid pain.002, delivered to SFTP outbound via work items.
package dev.thinkahead.sepa.reporting;

import static dev.thinkahead.sepa.Fixtures.bytes;
import static dev.thinkahead.sepa.Fixtures.uniqueMessageId;
import static dev.thinkahead.sepa.Fixtures.validPain001;
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
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;

@ApplicationModuleTest(mode = BootstrapMode.ALL_DEPENDENCIES)
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

    @Test
    void storesAndDeliversAcceptanceReport() throws Exception {
        String messageId = uniqueMessageId();
        String fileName = "report-accp-" + UUID.randomUUID() + ".xml";

        StatusReportDelivered delivered = submitAndAwaitDelivery(fileName, validPain001(messageId));

        assertThat(delivered.groupStatus()).isEqualTo(GroupStatus.ACCP);
        assertThat(delivered.reportMessageId()).hasSizeLessThanOrEqualTo(35);
        byte[] stored = storedReport(delivered.fileId());
        assertSchemaValid(stored);
        assertThat(text(parse(stored), "GrpHdr/MsgId")).isEqualTo(delivered.reportMessageId());
        assertThat(text(parse(stored), "OrgnlGrpInfAndSts/OrgnlMsgId")).isEqualTo(messageId);
        assertThat(customer.download("outbound", reportName(fileName, delivered.fileId()))).isEqualTo(stored);
    }

    @Test
    void storesAndDeliversRejectionReportWithReasons(CapturedOutput output) throws Exception {
        StatusReportDelivered delivered = submitAndAwaitDelivery("report-rjct-" + UUID.randomUUID() + ".xml",
                validPain001(uniqueMessageId()).replace("NL91ABNA0417164300", "NL91ABNA0417164301"));

        assertThat(delivered.groupStatus()).isEqualTo(GroupStatus.RJCT);
        byte[] stored = storedReport(delivered.fileId());
        assertSchemaValid(stored);
        assertThat(text(parse(stored), "OrgnlGrpInfAndSts/StsRsnInf[1]/Rsn/Cd")).isEqualTo("AC03");
        assertThat(LogLines.messagesForFile(output, delivered.fileId()))
                .contains("Delivered status report " + delivered.reportMessageId() + " with group status RJCT");
    }

    @Test
    void aReDrivenReportItemDoesNotCreateASecondReport() {
        String fileName = "report-redriven-" + UUID.randomUUID() + ".xml";
        StatusReportDelivered first = submitAndAwaitDelivery(fileName, validPain001(uniqueMessageId()));

        reDrive(first.fileId(), "REPORT");

        await().atMost(TIMEOUT).until(() -> itemState(first.fileId(), "REPORT").equals("DONE"));
        await().during(Duration.ofSeconds(2)).atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(jdbc.sql("select count(*) from status_report where file_id = ?").param(first.fileId())
                    .query(Integer.class).single()).isEqualTo(1);
            assertThat(deliveriesFor(first.fileId())).isEqualTo(1);
        });
        assertThat(storedReportMessageId(first.fileId())).isEqualTo(first.reportMessageId());
        assertThat(customer.list("outbound")).filteredOn(name -> name.startsWith(fileName.replace(".xml", "")))
                .hasSize(1);
    }

    @Test
    void aReDrivenDeliveryResendsTheStoredReportUnchanged() {
        String fileName = "report-resent-" + UUID.randomUUID() + ".xml";
        StatusReportDelivered first = submitAndAwaitDelivery(fileName, validPain001(uniqueMessageId()));
        byte[] stored = storedReport(first.fileId());
        String reportName = reportName(fileName, first.fileId());

        reDrive(first.fileId(), "DELIVER");

        await().atMost(TIMEOUT).until(() -> deliveriesFor(first.fileId()) == 2);
        assertThat(customer.download("outbound", reportName)).isEqualTo(stored);
        assertThat(customer.list("outbound")).filteredOn(name -> name.startsWith(fileName.replace(".xml", "")))
                .hasSize(1);
        assertThat(storedReportMessageId(first.fileId())).isEqualTo(first.reportMessageId());
    }

    @Test
    void deliveryIsRetriedUntilTheOutboundChannelRecovers() {
        String fileName = "report-retried-" + UUID.randomUUID() + ".xml";

        customer.chmod(".", "outbound", 0555);
        UUID fileId;
        try {
            fileId = upload(fileName, validPain001(uniqueMessageId()));

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
        assertThat(itemState(fileId, "DELIVER")).isEqualTo("DONE");
    }

    private StatusReportDelivered submitAndAwaitDelivery(String fileName, String xml) {
        UUID fileId = upload(fileName, xml);
        return await().atMost(TIMEOUT).until(
                () -> events.ofType(StatusReportDelivered.class).stream()
                        .filter(e -> e.fileId().equals(fileId)).findFirst().orElse(null),
                e -> e != null);
    }

    private UUID upload(String fileName, String xml) {
        customer.upload("inbound", fileName, bytes(xml));
        return await().atMost(TIMEOUT).until(
                () -> events.ofType(PaymentFileReceived.class).stream()
                        .filter(e -> e.fileName().equals(fileName)).map(PaymentFileReceived::fileId)
                        .findFirst().orElse(null),
                id -> id != null);
    }

    private void reDrive(UUID fileId, String step) {
        jdbc.sql("select redrive_work_item(?, ?, 'test', 'integration test re-drive')").params(fileId, step)
                .query(String.class).single();
    }

    private String itemState(UUID fileId, String step) {
        return jdbc.sql("select state from work_item where file_id = ? and step = ?").params(fileId, step)
                .query(String.class).single();
    }

    private long deliveriesFor(UUID fileId) {
        return events.ofType(StatusReportDelivered.class).stream().filter(e -> e.fileId().equals(fileId)).count();
    }

    private String storedReportMessageId(UUID fileId) {
        return jdbc.sql("select message_id from status_report where file_id = ?").param(fileId)
                .query(String.class).single();
    }

    private byte[] storedReport(UUID fileId) {
        return jdbc.sql("select content from status_report where file_id = ?").param(fileId)
                .query(byte[].class).single();
    }

    private static String reportName(String fileName, UUID fileId) {
        return fileName.replace(".xml", "") + "-" + fileId + ".pain002.xml";
    }
}
