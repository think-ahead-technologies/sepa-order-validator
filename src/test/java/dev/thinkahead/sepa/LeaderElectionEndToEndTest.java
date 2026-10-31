// ABOUTME: End-to-end tests running two full validator instances against one PostgreSQL and one SFTP server.
// ABOUTME: Proves each file is processed exactly once, and that leadership fails over on shutdown and crash.
package dev.thinkahead.sepa;

import static dev.thinkahead.sepa.Fixtures.uniqueMessageId;
import static dev.thinkahead.sepa.Fixtures.validPain001;
import static dev.thinkahead.sepa.Fixtures.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.sshd.sftp.client.SftpClient;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.util.UUIDConverter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

class LeaderElectionEndToEndTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String LEADER_LOCK_KEY = UUIDConverter.getUUID("sftp-intake").toString();
    private static final String LEADER_LOCK_REGION = "sepa";

    private final PostgreSQLContainer postgres = TestcontainersConfiguration.newPostgresContainer();
    private final GenericContainer<?> sftp = TestcontainersConfiguration.newSftpContainer();
    private final List<ConfigurableApplicationContext> instances = new ArrayList<>();

    private JdbcClient jdbc;
    private SftpTestClient customer;

    @BeforeEach
    void startInfrastructure() {
        postgres.start();
        sftp.start();
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        jdbc = JdbcClient.create(dataSource);
    }

    @AfterEach
    void stopEverything() {
        instances.forEach(ConfigurableApplicationContext::close);
        sftp.stop();
        postgres.stop();
    }

    @Test
    void twoInstancesProcessEveryFileExactlyOnce() {
        startInstance();
        awaitLeaderLock();
        startInstance();

        List<String> fileNames = IntStream.range(0, 10).mapToObj(i -> "cluster-" + UUID.randomUUID() + ".xml").toList();
        fileNames.forEach(name -> customer.upload("inbound", name, bytes(validPain001(uniqueMessageId()))));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(customer.list("outbound")).hasSize(fileNames.size()));
        assertThat(jdbc.sql("select file_name from payment_file").query(String.class).list())
                .containsExactlyInAnyOrderElementsOf(fileNames);
        assertThat(jdbc.sql("select count(*) from status_report").query(Integer.class).single())
                .isEqualTo(fileNames.size());
    }

    @Test
    void followerTakesOverWhenTheLeaderShutsDown() {
        ConfigurableApplicationContext leader = startInstance();
        String leaderClient = awaitLeaderLock();
        startInstance();

        leader.close();
        String fileName = "failover-" + UUID.randomUUID() + ".xml";
        customer.upload("inbound", fileName, bytes(validPain001(uniqueMessageId())));

        awaitStatusReportFor(fileName);
        assertThat(currentLockHolder()).isNotEqualTo(leaderClient);
    }

    @Test
    void instanceTakesOverOnceACrashedLeadersLockExpires() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load().migrate();
        String crashedClient = UUID.randomUUID().toString();
        jdbc.sql("""
                        insert into int_lock (lock_key, region, client_id, created_date, expired_after)
                        values (?, ?, ?, now() at time zone 'utc', now() at time zone 'utc' + interval '5 seconds')""")
                .params(LEADER_LOCK_KEY, LEADER_LOCK_REGION, crashedClient)
                .update();

        startInstance();
        String fileName = "after-crash-" + UUID.randomUUID() + ".xml";
        customer.upload("inbound", fileName, bytes(validPain001(uniqueMessageId())));

        await().during(Duration.ofMillis(1_500)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(customer.list("inbound")).as("not polled while the crashed leader's lock is valid")
                        .contains(fileName));

        awaitStatusReportFor(fileName);
        assertThat(currentLockHolder()).isNotEqualTo(crashedClient);
    }

    @SuppressWarnings("unchecked")
    private ConfigurableApplicationContext startInstance() {
        Map<String, Object> properties = new HashMap<>(TestcontainersConfiguration.applicationProperties(sftp));
        properties.put("spring.datasource.url", postgres.getJdbcUrl());
        properties.put("spring.datasource.username", postgres.getUsername());
        properties.put("spring.datasource.password", postgres.getPassword());

        String[] commandLine = properties.entrySet().stream()
                .map(property -> "--" + property.getKey() + "=" + property.getValue())
                .toArray(String[]::new);
        ConfigurableApplicationContext instance = new SpringApplicationBuilder(PaymentOrderValidatorApplication.class)
                .run(commandLine);
        instances.add(instance);
        if (customer == null) {
            customer = new SftpTestClient(instance.getBean(SessionFactory.class));
        }
        return instance;
    }

    private String awaitLeaderLock() {
        return await().atMost(TIMEOUT).until(this::currentLockHolder, holder -> holder != null);
    }

    private String currentLockHolder() {
        return jdbc.sql("""
                        select client_id from int_lock
                        where region = ? and lock_key = ? and expired_after > now() at time zone 'utc'""")
                .params(LEADER_LOCK_REGION, LEADER_LOCK_KEY)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private void awaitStatusReportFor(String fileName) {
        String reportPrefix = fileName.replace(".xml", "") + "-";
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(customer.list("outbound"))
                .anySatisfy(name -> assertThat(name).startsWith(reportPrefix)));
    }
}
