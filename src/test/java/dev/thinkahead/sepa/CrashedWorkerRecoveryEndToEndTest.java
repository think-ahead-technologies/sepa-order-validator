// ABOUTME: End-to-end test of a delivery worker killed mid-step: its lease blocks the item until it expires.
// ABOUTME: Another instance then takes the item over and the customer receives exactly one status report.
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

class CrashedWorkerRecoveryEndToEndTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

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
        jdbc = JdbcClient.create(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    @AfterEach
    void stopEverything() {
        instances.forEach(ConfigurableApplicationContext::close);
        sftp.stop();
        postgres.stop();
    }

    @Test
    void deliveryLeasedByAKilledWorkerIsTakenOverOnceTheLeaseExpires() {
        String fileName = "worker-killed-" + UUID.randomUUID() + ".xml";
        String reportPrefix = fileName.replace(".xml", "") + "-";

        ConfigurableApplicationContext crashed = startInstance();
        customer.chmod(".", "outbound", 0555);
        customer.upload("inbound", fileName, bytes(validPain001(uniqueMessageId())));
        await().atMost(TIMEOUT)
                .until(() -> jdbc.sql("""
                        select count(*) from work_item w join payment_file f on f.id = w.file_id
                        where f.file_name = ? and w.step = 'DELIVER' and w.attempts >= 1""").param(fileName).query(Integer.class).single() == 1);
        crashed.close();
        customer.chmod(".", "outbound", 0755);
        // What a pod killed in the middle of the delivery leaves behind: the item leased to a worker that is gone.
        jdbc.sql("""
                        update work_item set lease_token = gen_random_uuid(), locked_until = now() + interval '5 seconds',
                                             next_attempt_at = now()
                        where step = 'DELIVER' and state = 'PENDING'""").update();

        startInstance();

        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(4))
                .untilAsserted(() -> assertThat(customer.list("outbound"))
                        .as("leased item is left alone while the lease is valid")
                        .noneMatch(name -> name.startsWith(reportPrefix)));
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(customer.list("outbound"))
                        .filteredOn(name -> name.startsWith(reportPrefix))
                        .hasSize(1));
        await().atMost(TIMEOUT)
                .untilAsserted(
                        () -> assertThat(jdbc.sql("select replied_at is not null from payment_file where file_name = ?")
                                        .param(fileName)
                                        .query(Boolean.class)
                                        .single())
                                .isTrue());
        assertThat(jdbc.sql("""
                        select count(*) from audit_event a join payment_file f on f.id = a.file_id
                        where f.file_name = ? and a.event_type = 'StatusReportDelivered'""").param(fileName).query(Integer.class).single()).isEqualTo(1);
    }

    private ConfigurableApplicationContext startInstance() {
        Map<String, Object> properties = new HashMap<>(TestcontainersConfiguration.applicationProperties(sftp));
        properties.put("spring.datasource.url", postgres.getJdbcUrl());
        properties.put("spring.datasource.username", postgres.getUsername());
        properties.put("spring.datasource.password", postgres.getPassword());

        ConfigurableApplicationContext instance = new SpringApplicationBuilder(PaymentOrderValidatorApplication.class)
                .run(properties.entrySet().stream()
                        .map(property -> "--" + property.getKey() + "=" + property.getValue())
                        .toArray(String[]::new));
        instances.add(instance);
        return instance;
    }
}
