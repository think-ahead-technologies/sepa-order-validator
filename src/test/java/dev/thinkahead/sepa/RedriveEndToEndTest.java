// ABOUTME: End-to-end test of the operations runbook for a delivery that failed for good.
// ABOUTME: After the cause is fixed, redrive_work_item delivers the report exactly once and audits the intervention.
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

@SpringBootTest(properties = "sepa.processing.deliver.max-attempts=2")
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
class RedriveEndToEndTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    SftpTestClient customer;

    @Autowired
    JdbcClient jdbc;

    @Test
    void aFailedDeliveryIsDeliveredOnceAfterTheRunbookReDrive() {
        String fileName = "redrive-" + UUID.randomUUID() + ".xml";
        String reportPrefix = fileName.replace(".xml", "") + "-";

        customer.chmod(".", "outbound", 0555);
        try {
            customer.upload("inbound", fileName, bytes(validPain001(uniqueMessageId())));
            await().atMost(TIMEOUT).until(() -> "FAILED".equals(deliverState(fileName)));
        } finally {
            customer.chmod(".", "outbound", 0755);
        }
        UUID fileId = jdbc.sql("select id from payment_file where file_name = ?")
                .param(fileName)
                .query(UUID.class)
                .single();

        jdbc.sql("select redrive_work_item(?, 'DELIVER', 'ops.on.call', 'outbound permissions repaired')")
                .param(fileId)
                .query(String.class)
                .single();

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(customer.list("outbound"))
                        .filteredOn(name -> name.startsWith(reportPrefix))
                        .hasSize(1));
        await().atMost(TIMEOUT).until(() -> "DONE".equals(deliverState(fileName)));
        assertThat(jdbc.sql("select event_type from audit_event where file_id = ? order by recorded_at")
                        .param(fileId)
                        .query(String.class)
                        .list())
                .containsExactly(
                        "PaymentFileReceived", "PaymentFileValidated", "WorkItemRedriven", "StatusReportDelivered");
    }

    private String deliverState(String fileName) {
        return jdbc.sql("""
                        select w.state from work_item w join payment_file f on f.id = w.file_id
                        where f.file_name = ? and w.step = 'DELIVER'""").param(fileName).query(String.class).optional().orElse(null);
    }
}
