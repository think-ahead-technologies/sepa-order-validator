// ABOUTME: End-to-end test of a delivery attempt hanging on an unresponsive SFTP server (container paused).
// ABOUTME: The worker stops the attempt before its lease ends and retries; once the server is back it delivers once.
package dev.thinkahead.sepa;

import static dev.thinkahead.sepa.Fixtures.bytes;
import static dev.thinkahead.sepa.Fixtures.uniqueMessageId;
import static dev.thinkahead.sepa.Fixtures.validPain001;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

// The SFTP timeout is longer than the DELIVER step timeout, so the worker, not the SFTP client, ends the attempt.
@SpringBootTest(properties = "sepa.sftp.timeout=PT1M")
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
class HungDeliveryEndToEndTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Autowired
    SftpTestClient customer;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    GenericContainer<?> sftp;

    @Test
    void anAttemptHangingOnTheSftpServerIsStoppedAndRetried() {
        String fileName = "hung-" + UUID.randomUUID() + ".xml";
        String reportPrefix = fileName.replace(".xml", "") + "-";
        customer.chmod(".", "outbound", 0555);
        customer.upload("inbound", fileName, bytes(validPain001(uniqueMessageId())));
        await().atMost(TIMEOUT).until(() -> deliverAttempts(fileName) >= 1);

        var docker = DockerClientFactory.instance().client();
        docker.pauseContainerCmd(sftp.getContainerId()).exec();
        try {
            await().atMost(TIMEOUT).untilAsserted(() -> assertThat(jdbc.sql("""
                            select w.last_error from work_item w join payment_file f on f.id = w.file_id
                            where f.file_name = ? and w.step = 'DELIVER'""").param(fileName)
                    .query(String.class).single()).contains("did not finish within"));
        } finally {
            docker.unpauseContainerCmd(sftp.getContainerId()).exec();
        }
        customer.chmod(".", "outbound", 0755);

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(customer.list("outbound"))
                .filteredOn(name -> name.startsWith(reportPrefix)).hasSize(1));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(jdbc.sql("""
                        select w.state from work_item w join payment_file f on f.id = w.file_id
                        where f.file_name = ? and w.step = 'DELIVER'""").param(fileName)
                .query(String.class).single()).isEqualTo("DONE"));
    }

    private int deliverAttempts(String fileName) {
        return jdbc.sql("""
                        select coalesce(max(w.attempts), 0) from work_item w join payment_file f on f.id = w.file_id
                        where f.file_name = ? and w.step = 'DELIVER'""").param(fileName).query(Integer.class).single();
    }
}
