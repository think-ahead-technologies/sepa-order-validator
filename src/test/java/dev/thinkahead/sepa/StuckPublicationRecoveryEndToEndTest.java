// ABOUTME: End-to-end test of recovery from an instance killed while a module event listener was running.
// ABOUTME: A starting instance leaves the in-progress work alone; once stale it is resubmitted and finished exactly once.
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
    private static final String DELIVERY_LISTENER = "%StatusReportDeliveryListener%";

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
    void deliveryInterruptedByACrashIsFinishedOnceItIsStaleAndNotEarlier() {
        String fileName = "crash-during-delivery-" + UUID.randomUUID() + ".xml";
        String reportPrefix = fileName.replace(".xml", "") + "-";

        ConfigurableApplicationContext crashed = startInstance();
        customer.chmod(".", "outbound", 0555);
        customer.upload("inbound", fileName, bytes(validPain001(uniqueMessageId())));
        await().atMost(TIMEOUT).until(() -> deliveryPublications("FAILED") == 1);
        crashed.close();
        customer.chmod(".", "outbound", 0755);
        // What a pod killed in the middle of the delivery listener leaves behind.
        jdbc.sql("""
                        update event_publication
                        set status = 'PROCESSING', publication_date = now(), last_resubmission_date = null
                        where listener_id like ? and completion_date is null""")
                .param(DELIVERY_LISTENER).update();

        startInstance();

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(4)).untilAsserted(() ->
                assertThat(customer.list("outbound")).as("work in progress elsewhere is not taken over on startup")
                        .noneMatch(name -> name.startsWith(reportPrefix)));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(customer.list("outbound"))
                .filteredOn(name -> name.startsWith(reportPrefix)).hasSize(1));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(jdbc.sql(
                        "select replied_at is not null from payment_file where file_name = ?")
                .param(fileName).query(Boolean.class).single()).isTrue());
    }

    private int deliveryPublications(String status) {
        return jdbc.sql("""
                        select count(*) from event_publication
                        where listener_id like ? and status = ? and completion_date is null""")
                .params(DELIVERY_LISTENER, status).query(Integer.class).single();
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
