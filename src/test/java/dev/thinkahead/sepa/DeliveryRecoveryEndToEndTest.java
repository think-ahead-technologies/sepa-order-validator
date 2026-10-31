// ABOUTME: End-to-end test of recovery from a failed processing step: SFTP outbound is temporarily read-only.
// ABOUTME: The failed event delivery is resubmitted automatically and the customer receives exactly one report.
package dev.thinkahead.sepa;

import static dev.thinkahead.sepa.Fixtures.bytes;
import static dev.thinkahead.sepa.Fixtures.uniqueMessageId;
import static dev.thinkahead.sepa.Fixtures.validPain001;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = {
        "sepa.events.resubmit-after=PT3S",
        "sepa.events.resubmit-interval=PT1S"
})
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
class DeliveryRecoveryEndToEndTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    SftpTestClient customer;

    @Autowired
    JdbcClient jdbc;

    @Test
    void reportIsDeliveredExactlyOnceAfterTheOutboundChannelRecovers(CapturedOutput output) {
        String fileName = "outage-" + UUID.randomUUID() + ".xml";
        String reportPrefix = fileName.replace(".xml", "") + "-";

        customer.chmod(".", "outbound", 0555);
        try {
            customer.upload("inbound", fileName, bytes(validPain001(uniqueMessageId())));

            await().atMost(TIMEOUT).untilAsserted(() ->
                    assertThat(output).contains("Cannot write SFTP outbound file " + reportPrefix));
            assertThat(isReplied(fileName)).as("no reply while the channel is down").isFalse();
            UUID fileId = jdbc.sql("select id from payment_file where file_name = ?").param(fileName)
                    .query(UUID.class).single();
            assertThat(LogLines.forFile(output, fileId)).as("delivery failure logged with the file id")
                    .anySatisfy(line -> {
                        assertThat(line.path("log").path("level").asString()).isEqualTo("ERROR");
                        assertThat(line.path("error").path("message").asString())
                                .startsWith("Cannot write SFTP outbound file " + reportPrefix);
                    });
        } finally {
            customer.chmod(".", "outbound", 0755);
        }

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(customer.list("outbound"))
                .filteredOn(name -> name.startsWith(reportPrefix)).hasSize(1));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(isReplied(fileName)).isTrue());
        assertThat(jdbc.sql("""
                        select count(*) from status_report r join payment_file f on f.id = r.file_id
                        where f.file_name = ? and r.delivered_at is not null""")
                .param(fileName).query(Integer.class).single()).isEqualTo(1);
    }

    private boolean isReplied(String fileName) {
        return jdbc.sql("select replied_at is not null from payment_file where file_name = ?").param(fileName)
                .query(Boolean.class).single();
    }
}
