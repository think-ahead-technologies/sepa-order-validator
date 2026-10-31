// ABOUTME: Integration test of the Flyway migration that moves status report delivery onto the work queue.
// ABOUTME: Undelivered reports get a DELIVER work item and their orphaned delivery event publications are removed.
package dev.thinkahead.sepa.processing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.thinkahead.sepa.TestcontainersConfiguration;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class WorkItemMigrationIntegrationTest {

    private static final String DELIVERY_LISTENER = "dev.thinkahead.sepa.reporting.StatusReportDeliveryListener.on("
            + "dev.thinkahead.sepa.reporting.StatusReportCreated)";

    private final PostgreSQLContainer postgres = TestcontainersConfiguration.newPostgresContainer();

    private JdbcClient jdbc;

    @BeforeEach
    void startDatabase() {
        postgres.start();
        jdbc = JdbcClient.create(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()));
    }

    @AfterEach
    void stopDatabase() {
        postgres.stop();
    }

    @Test
    void movesUndeliveredReportsToTheWorkQueue() {
        migrateTo("10");
        UUID undelivered = UUID.randomUUID();
        UUID delivered = UUID.randomUUID();
        insertReport(undelivered, false);
        insertReport(delivered, true);
        insertDeliveryPublication(undelivered, null);
        insertDeliveryPublication(delivered, "now()");

        migrateTo("latest");

        assertThat(jdbc.sql("select file_id from work_item where step = 'DELIVER' and state = 'PENDING'")
                .query(UUID.class).list()).containsExactly(undelivered);
        assertThat(jdbc.sql("select count(*) from event_publication where completion_date is null")
                .query(Integer.class).single()).as("orphaned delivery publications").isZero();
        assertThat(jdbc.sql("select count(*) from event_publication").query(Integer.class).single())
                .as("completed publications are left alone").isEqualTo(1);
    }

    private void migrateTo(String version) {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target(version).load().migrate();
    }

    private void insertReport(UUID fileId, boolean delivered) {
        jdbc.sql("""
                        insert into status_report (message_id, file_id, group_status, content, created_at, delivered_at)
                        values (?, ?, 'ACCP', ?, now(), %s)""".formatted(delivered ? "now()" : "null"))
                .params("STS" + fileId.toString().replace("-", ""), fileId, "<Document/>".getBytes())
                .update();
    }

    private void insertDeliveryPublication(UUID fileId, String completionDate) {
        jdbc.sql("""
                        insert into event_publication (id, listener_id, event_type, serialized_event,
                                                       publication_date, completion_date, status)
                        values (?, ?, 'dev.thinkahead.sepa.reporting.StatusReportCreated', ?, now(), %s, ?)"""
                        .formatted(completionDate == null ? "null" : completionDate))
                .params(UUID.randomUUID(), DELIVERY_LISTENER, "{\"fileId\":\"" + fileId + "\"}",
                        completionDate == null ? "PROCESSING" : "COMPLETED")
                .update();
    }
}
