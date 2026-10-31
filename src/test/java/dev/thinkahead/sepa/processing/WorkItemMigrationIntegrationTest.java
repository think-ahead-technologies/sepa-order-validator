// ABOUTME: Integration tests of the Flyway migrations that move delivery and validation onto the work queue.
// ABOUTME: Pending work becomes work items and only the removed listeners' event publications are dropped.
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
    private static final String VALIDATION_LISTENER = "dev.thinkahead.sepa.validation.PaymentFileValidationListener"
            + ".on(dev.thinkahead.sepa.intake.PaymentFileReceived)";
    private static final String AUDIT_LISTENER = "dev.thinkahead.sepa.audit.AuditLog"
            + ".on(dev.thinkahead.sepa.intake.PaymentFileReceived)";

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

    @Test
    void movesUnvalidatedFilesToTheWorkQueue() {
        migrateTo("11");
        UUID received = UUID.randomUUID();
        UUID accepted = UUID.randomUUID();
        insertFile(received, "RECEIVED");
        insertFile(accepted, "ACCEPTED");
        insertPublication(VALIDATION_LISTENER, "dev.thinkahead.sepa.intake.PaymentFileReceived", received, null);
        insertPublication(AUDIT_LISTENER, "dev.thinkahead.sepa.intake.PaymentFileReceived", received, null);

        migrateTo("latest");

        assertThat(jdbc.sql("select file_id from work_item where step = 'VALIDATE' and state = 'PENDING'")
                .query(UUID.class).list()).containsExactly(received);
        assertThat(jdbc.sql("select listener_id from event_publication where completion_date is null")
                .query(String.class).list()).as("only the removed validation listener's publications are dropped")
                .containsExactly(AUDIT_LISTENER);
    }

    private void insertFile(UUID fileId, String status) {
        jdbc.sql("""
                        insert into payment_file (id, channel, submitter, file_name, size_bytes, sha256, content, status)
                        values (?, 'SFTP', 'sftp:sepa', 'migrated.xml', 3, repeat('0', 64), ?, ?)""")
                .params(fileId, "<x>".getBytes(), status).update();
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
        insertPublication(DELIVERY_LISTENER, "dev.thinkahead.sepa.reporting.StatusReportCreated", fileId,
                completionDate);
    }

    private void insertPublication(String listenerId, String eventType, UUID fileId, String completionDate) {
        jdbc.sql("""
                        insert into event_publication (id, listener_id, event_type, serialized_event,
                                                       publication_date, completion_date, status)
                        values (?, ?, ?, ?, now(), %s, ?)""".formatted(completionDate == null ? "null" : completionDate))
                .params(UUID.randomUUID(), listenerId, eventType, "{\"fileId\":\"" + fileId + "\"}",
                        completionDate == null ? "PROCESSING" : "COMPLETED")
                .update();
    }
}
