// ABOUTME: End-to-end test of recovery from an instance killed while a module event listener (audit) was running.
// ABOUTME: A starting instance leaves the in-progress run alone; once stale it is resubmitted and recorded exactly once.
package dev.thinkahead.sepa;

import static dev.thinkahead.sepa.Fixtures.bytes;
import static dev.thinkahead.sepa.Fixtures.uniqueMessageId;
import static dev.thinkahead.sepa.Fixtures.validPain001;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

class StuckPublicationRecoveryEndToEndTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STALE_AFTER = Duration.ofSeconds(6);
    private static final String AUDIT_LISTENER = "%AuditLog.on(dev.thinkahead.sepa.intake.PaymentFileReceived)%";

    private final PostgreSQLContainer postgres = TestcontainersConfiguration.newPostgresContainer();
    private final GenericContainer<?> sftp = TestcontainersConfiguration.newSftpContainer();
    private final List<ConfigurableApplicationContext> instances = new ArrayList<>();

    private JdbcClient jdbc;
    private SftpTestClient customer;

    @BeforeEach
    void startInfrastructure() {
        postgres.start();
        sftp.start();
        customer = TestcontainersConfiguration.newCustomerClient(sftp);
        jdbc = JdbcClient.create(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()));
    }

    @AfterEach
    void stopEverything() {
        instances.forEach(ConfigurableApplicationContext::close);
        sftp.stop();
        postgres.stop();
    }

    @Test
    void auditListenerRunInterruptedByACrashIsFinishedOnceItIsStaleAndNotEarlier() {
        String fileName = "crash-during-audit-" + UUID.randomUUID() + ".xml";

        ConfigurableApplicationContext crashed = startInstance();
        jdbc.sql("""
                create function block_audit_insert() returns trigger language plpgsql as $$
                begin raise exception 'audit store unavailable'; end; $$""").update();
        jdbc.sql("""
                create trigger block_audit_insert before insert on audit_event
                for each row execute function block_audit_insert()""").update();
        customer.upload("inbound", fileName, bytes(validPain001(uniqueMessageId())));
        await().atMost(TIMEOUT).until(() -> auditPublications("FAILED") == 1);
        crashed.close();
        jdbc.sql("drop trigger block_audit_insert on audit_event").update();
        // What a pod killed in the middle of the audit listener leaves behind.
        jdbc.sql("""
                        update event_publication
                        set status = 'PROCESSING', publication_date = now(), last_resubmission_date = null
                        where listener_id like ? and completion_date is null""")
                .param(AUDIT_LISTENER).update();

        startInstance();

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(4)).untilAsserted(() ->
                assertThat(receivedAuditEntries(fileName)).as("run in progress elsewhere is not taken over on startup")
                        .isZero());
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(receivedAuditEntries(fileName)).isEqualTo(1));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(receivedAuditEntries(fileName)).as("recorded exactly once").isEqualTo(1));
    }

    private int auditPublications(String status) {
        return jdbc.sql("""
                        select count(*) from event_publication
                        where listener_id like ? and status = ? and completion_date is null""")
                .params(AUDIT_LISTENER, status).query(Integer.class).single();
    }

    private int receivedAuditEntries(String fileName) {
        return jdbc.sql("""
                        select count(*) from audit_event a join payment_file f on f.id = a.file_id
                        where f.file_name = ? and a.event_type = 'PaymentFileReceived'""")
                .param(fileName).query(Integer.class).single();
    }

    private ConfigurableApplicationContext startInstance() {
        Map<String, Object> properties = new HashMap<>(TestcontainersConfiguration.applicationProperties(sftp));
        properties.put("spring.datasource.url", postgres.getJdbcUrl());
        properties.put("spring.datasource.username", postgres.getUsername());
        properties.put("spring.datasource.password", postgres.getPassword());
        properties.put("spring.modulith.events.staleness.processing", STALE_AFTER.toString());
        properties.put("spring.modulith.events.staleness.check-interval", "PT1S");
        properties.put("sepa.events.resubmit-after", "PT1S");
        properties.put("sepa.events.resubmit-interval", "PT1S");

        ConfigurableApplicationContext instance = new SpringApplicationBuilder(PaymentOrderValidatorApplication.class)
                .run(properties.entrySet().stream()
                        .map(property -> "--" + property.getKey() + "=" + property.getValue())
                        .toArray(String[]::new));
        instances.add(instance);
        return instance;
    }
}
