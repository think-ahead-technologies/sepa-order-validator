// ABOUTME: Integration tests for the intake module alone against real SFTP and PostgreSQL containers.
// ABOUTME: Covers claiming, unfinished uploads, connection reuse, idempotency, archiving, failures and replies.
package dev.thinkahead.sepa.intake;

import static dev.thinkahead.sepa.Fixtures.VALID_PAIN_001;
import static dev.thinkahead.sepa.Fixtures.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.thinkahead.sepa.CapturedEvents;
import dev.thinkahead.sepa.Fixtures;
import dev.thinkahead.sepa.LogLines;
import dev.thinkahead.sepa.SftpTestClient;
import dev.thinkahead.sepa.TestSupportConfiguration;
import dev.thinkahead.sepa.TestcontainersConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
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
import org.testcontainers.containers.GenericContainer;

@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@ExtendWith(OutputCaptureExtension.class)
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

    @Autowired
    PaymentFileIntake intake;

    @Autowired
    SftpChannel sftpChannel;

    @Autowired
    UnansweredFileMonitor unansweredFiles;

    @Autowired
    GenericContainer<?> sftp;

    @Autowired
    MeterRegistry meters;

    @Test
    void anUnreadableFileDoesNotBlockTheFilesBehindIt(CapturedOutput output) {
        var poison = new ClaimedFile(UUID.fromString("00000000-0000-0000-0000-000000000000"),
                "poison-" + UUID.randomUUID() + ".xml");
        customer.upload("processing", poison.claimedName(), bytes(VALID_PAIN_001));
        customer.chmod("processing", poison.claimedName(), 0);
        try {
            String behind = "behind-poison-" + UUID.randomUUID() + ".xml";

            customer.upload("inbound", behind, bytes(VALID_PAIN_001));

            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(customer.list("processed"))
                    .anySatisfy(name -> assertThat(name).endsWith(behind)));
            assertThat(customer.list("processing")).contains(poison.claimedName());
            assertThat(output).contains("Could not take in claimed file " + poison.claimedName());
            assertThat(LogLines.messagesForFile(output, poison.fileId()))
                    .anySatisfy(message -> assertThat(message).startsWith("Could not take in claimed file"));
        } finally {
            customer.chmod("processing", poison.claimedName(), 0644);
        }
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(customer.list("processed")).contains(poison.claimedName()));
    }

    @Test
    void receivesAMessageFromAContainerOnceAsItsOwnFile() {
        UUID containerId = UUID.randomUUID();
        intake.receive(containerId, Channel.SFTP, "sftp:sepa", "orders.xml", ReceivedContent.of(bytes(VALID_PAIN_001)));

        UUID message = paymentFiles.receiveFromContainer(containerId, 1, bytes(VALID_PAIN_001));
        UUID again = paymentFiles.receiveFromContainer(containerId, 1, bytes(VALID_PAIN_001));

        assertThat(again).isEqualTo(message);
        assertThat(jdbc.sql("""
                        select file_name, channel, submitter, container_file_id, container_index
                        from payment_file where id = ?""").param(message).query().singleRow())
                .containsEntry("file_name", "orders-msg1.xml")
                .containsEntry("channel", "SFTP")
                .containsEntry("submitter", "sftp:sepa")
                .containsEntry("container_file_id", containerId)
                .containsEntry("container_index", 1);
        assertThat(jdbc.sql("select count(*) from work_item where file_id = ? and step = 'VALIDATE'").param(message)
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(paymentFiles.content(message)).hasValueSatisfying(
                content -> assertThat(content).isEqualTo(bytes(VALID_PAIN_001)));
    }

    @Test
    void doesNotAlertAboutAContainerThatWasSplitIntoMessages() {
        UUID containerId = UUID.randomUUID();
        intake.receive(containerId, Channel.SFTP, "sftp:sepa", "split.xml", ReceivedContent.of(bytes(VALID_PAIN_001)));
        jdbc.sql("update payment_file set received_at = now() - interval '1 hour' where id = ?").param(containerId)
                .update();

        assertThat(paymentFiles.recordSplit(containerId)).isTrue();

        assertThat(unansweredFiles.check()).doesNotContain(containerId);
        assertThat(paymentFiles.isAwaitingValidation(containerId)).isFalse();
    }

    @Test
    void alertsAboutFilesStillWithoutReplyAfterTheThreshold(CapturedOutput output) {
        UUID fileId = UUID.randomUUID();
        intake.receive(fileId, Channel.SFTP, "sftp:sepa", "overdue.xml", ReceivedContent.of(bytes(VALID_PAIN_001)));
        double unansweredBefore = unansweredGauge();
        jdbc.sql("update payment_file set received_at = now() - interval '1 hour' where id = ?").param(fileId).update();

        assertThat(unansweredFiles.check()).contains(fileId);
        assertThat(output).contains("without a reply").contains(fileId.toString());
        assertThat(unansweredGauge()).isEqualTo(unansweredBefore + 1);

        paymentFiles.markReplied(fileId);

        assertThat(unansweredFiles.check()).doesNotContain(fileId);
        assertThat(unansweredGauge()).isEqualTo(unansweredBefore);
        assertThat(jdbc.sql("select replied_at is not null from payment_file where id = ?").param(fileId)
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void onlyOneClaimSucceedsForTheSameInboundFile() {
        String fileName = "contested-" + UUID.randomUUID() + ".tmp";
        customer.upload("inbound", fileName, bytes(VALID_PAIN_001));

        boolean first = sftpChannel.claim(fileName, new ClaimedFile(UUID.randomUUID(), fileName).claimedName());
        boolean second = sftpChannel.claim(fileName, new ClaimedFile(UUID.randomUUID(), fileName).claimedName());

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(customer.list("processing")).filteredOn(name -> name.endsWith(fileName)).hasSize(1);
    }

    @Test
    void recoversAFileClaimedByAnInstanceThatCrashedBeforeReceivingIt() {
        var claimed = new ClaimedFile(UUID.randomUUID(), "crashed-before-receive-" + UUID.randomUUID() + ".xml");

        customer.upload("processing", claimed.claimedName(), bytes(VALID_PAIN_001));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(customer.list("processed")).contains(claimed.claimedName());
            assertThat(customer.list("processing")).doesNotContain(claimed.claimedName());
        });
        assertThat(recordsWithId(claimed.fileId())).isEqualTo(1);
        assertThat(receivedEventsFor(claimed.fileId())).isEqualTo(1);
        assertThat(jdbc.sql("select file_name from payment_file where id = ?").param(claimed.fileId())
                .query(String.class).single()).isEqualTo(claimed.originalName());
    }

    @Test
    void doesNotReceiveAgainWhenAnInstanceCrashedAfterReceivingButBeforeArchiving() {
        var claimed = new ClaimedFile(UUID.randomUUID(), "crashed-after-receive-" + UUID.randomUUID() + ".xml");
        byte[] content = bytes(VALID_PAIN_001);
        assertThat(intake.receive(claimed.fileId(), Channel.SFTP, "sftp:sepa", claimed.originalName(), ReceivedContent.of(content)))
                .isTrue();

        customer.upload("processing", claimed.claimedName(), content);

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(customer.list("processed")).contains(claimed.claimedName()));
        assertThat(recordsWithId(claimed.fileId())).isEqualTo(1);
        assertThat(receivedEventsFor(claimed.fileId())).isEqualTo(1);
    }

    @Test
    void receivingTheSameClaimTwiceStoresAndAnnouncesItOnce() {
        UUID fileId = UUID.randomUUID();
        byte[] content = bytes(VALID_PAIN_001);

        boolean first = intake.receive(fileId, Channel.SFTP, "sftp:sepa", "twice.xml", ReceivedContent.of(content));
        boolean second = intake.receive(fileId, Channel.SFTP, "sftp:sepa", "twice.xml", ReceivedContent.of(content));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(recordsWithId(fileId)).isEqualTo(1);
        assertThat(receivedEventsFor(fileId)).isEqualTo(1);
    }

    @Test
    void storesOnlySizeAndHashOfAFileAboveTheMaximumSize() throws Exception {
        String fileName = "oversized-" + UUID.randomUUID() + ".xml";
        byte[] content = bytes(Fixtures.paddedTo(VALID_PAIN_001, 1_100_000));

        customer.upload("inbound", fileName, content);

        PaymentFileReceived received = await().atMost(TIMEOUT).until(
                () -> events.ofType(PaymentFileReceived.class).stream()
                        .filter(e -> e.fileName().equals(fileName)).findFirst().orElse(null),
                e -> e != null);
        assertThat(received.sha256())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
        assertThat(paymentFiles.content(received.fileId())).isEmpty();
        assertThat(jdbc.sql("select size_bytes from payment_file where id = ?").param(received.fileId())
                .query(Long.class).single()).isEqualTo(content.length);
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(customer.list("processed"))
                .anySatisfy(name -> assertThat(name).endsWith(fileName)));
    }

    private int recordsWithId(UUID fileId) {
        return jdbc.sql("select count(*) from payment_file where id = ?").param(fileId).query(Integer.class).single();
    }

    private long receivedEventsFor(UUID fileId) {
        return events.ofType(PaymentFileReceived.class).stream().filter(e -> e.fileId().equals(fileId)).count();
    }

    @Test
    void storesUploadedFileWithHashAndPublishesEvent(CapturedOutput output) throws Exception {
        String fileName = "intake-" + UUID.randomUUID() + ".xml";
        byte[] content = bytes(VALID_PAIN_001);

        customer.upload("inbound", fileName, content);

        PaymentFileReceived received = await().atMost(TIMEOUT).until(
                () -> events.ofType(PaymentFileReceived.class).stream()
                        .filter(e -> e.fileName().equals(fileName)).findFirst().orElse(null),
                e -> e != null);

        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        assertThat(received.channel()).isEqualTo(Channel.SFTP);
        assertThat(received.submitter()).isEqualTo("sftp:sepa");
        assertThat(received.sha256()).isEqualTo(expectedHash);
        assertThat(jdbc.sql("select status, sha256, channel, submitter from payment_file where id = ?")
                .param(received.fileId())
                .query((rs, n) -> rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3)
                        + "|" + rs.getString(4))
                .single())
                .isEqualTo("RECEIVED|" + expectedHash + "|SFTP|sftp:sepa");
        assertThat(paymentFiles.content(received.fileId())).hasValueSatisfying(stored -> assertThat(stored).isEqualTo(content));
        assertThat(LogLines.messagesForFile(output, received.fileId()))
                .contains("Received payment file " + fileName + " (" + content.length + " bytes) from sftp:sepa");
        assertThat(jdbc.sql("select count(*) from work_item where file_id = ? and step = 'VALIDATE'")
                .param(received.fileId()).query(Integer.class).single()).as("validation scheduled").isEqualTo(1);
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
    void ignoresFilesThatAreNotXml() {
        String fileName = "upload-in-progress-" + UUID.randomUUID() + ".tmp";

        customer.upload("inbound", fileName, bytes(VALID_PAIN_001));

        await().during(Duration.ofMillis(1_500)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(customer.list("inbound")).as("left alone across several poll cycles").contains(fileName));
    }

    @Test
    void doesNotClaimAnXmlFileWhileItIsStillBeingUploaded() {
        String fileName = "slow-upload-" + UUID.randomUUID() + ".xml";
        byte[] content = bytes(VALID_PAIN_001);

        customer.uploadSlowly("inbound", fileName, content, 20, Duration.ofMillis(100), () ->
                assertThat(customer.list("inbound")).as("left alone while still growing").contains(fileName));

        PaymentFileReceived received = await().atMost(TIMEOUT).until(
                () -> events.ofType(PaymentFileReceived.class).stream()
                        .filter(e -> e.fileName().equals(fileName)).findFirst().orElse(null),
                e -> e != null);
        assertThat(paymentFiles.content(received.fileId()))
                .hasValueSatisfying(stored -> assertThat(stored).isEqualTo(content));
    }

    @Test
    void reusesItsSshConnectionAcrossPolls() {
        await().atMost(TIMEOUT).until(() -> sshLogins() > 0);
        long loginsBefore = sshLogins();

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(sshLogins()).as("no new SSH login across several polls").isEqualTo(loginsBefore));
    }

    private double unansweredGauge() {
        return meters.get("sepa.intake.files.unanswered").gauge().value();
    }

    private long sshLogins() {
        return sftp.getLogs().lines().filter(line -> line.contains("Accepted password for")).count();
    }

    @Test
    void recordsValidationOutcome() {
        UUID fileId = receive("outcome-" + UUID.randomUUID() + ".xml");

        paymentFiles.recordValidationOutcome(fileId, false);

        assertThat(jdbc.sql("select status from payment_file where id = ?").param(fileId)
                .query(String.class).single()).isEqualTo("REJECTED");
    }

    @Test
    void writesStatusReportThroughTheSftpChannelViaTheStagingDirectory() {
        String fileName = "deliver-" + UUID.randomUUID() + ".xml";
        UUID fileId = receive(fileName);
        byte[] report = bytes("<Document/>");

        paymentFiles.writeStatusReport(fileId, report);

        String reportName = fileName.replace(".xml", "") + "-" + fileId + ".pain002.xml";
        assertThat(customer.list("outbound")).contains(reportName);
        assertThat(customer.download("outbound", reportName)).isEqualTo(report);
        assertThat(customer.list("staging")).doesNotContain(reportName);
    }

    @Test
    void writingTheSameStatusReportAgainReplacesItUnderTheSameName() {
        String fileName = "deliver-twice-" + UUID.randomUUID() + ".xml";
        UUID fileId = receive(fileName);
        byte[] report = bytes("<Document/>");

        paymentFiles.writeStatusReport(fileId, report);
        paymentFiles.writeStatusReport(fileId, report);

        String reportName = fileName.replace(".xml", "") + "-" + fileId + ".pain002.xml";
        assertThat(customer.list("outbound")).filteredOn(name -> name.equals(reportName)).hasSize(1);
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
