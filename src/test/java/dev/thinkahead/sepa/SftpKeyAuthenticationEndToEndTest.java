// ABOUTME: End-to-end test of the full application logging in to SFTP with a private key instead of a password.
// ABOUTME: A customer file is picked up and answered with a pain.002 over the key-authenticated connection.
package dev.thinkahead.sepa;

import static dev.thinkahead.sepa.Fixtures.bytes;
import static dev.thinkahead.sepa.Fixtures.uniqueMessageId;
import static dev.thinkahead.sepa.Fixtures.validPain001;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

class SftpKeyAuthenticationEndToEndTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String PASSPHRASE = "e2e-key-passphrase";

    private final PostgreSQLContainer postgres = TestcontainersConfiguration.newPostgresContainer();
    private final GenericContainer<?> sftp = TestcontainersConfiguration.newSftpContainer();

    private ConfigurableApplicationContext application;

    @BeforeEach
    void startInfrastructure() {
        postgres.start();
        sftp.start();
    }

    @AfterEach
    void stopEverything() {
        if (application != null) {
            application.close();
        }
        sftp.stop();
        postgres.stop();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fileIsAnsweredOverAKeyAuthenticatedConnection() {
        Path privateKey = TestcontainersConfiguration.newClientKey(sftp, PASSPHRASE, true);
        Map<String, Object> properties = new HashMap<>(TestcontainersConfiguration.applicationProperties(sftp));
        properties.remove("sepa.sftp.password");
        properties.put("sepa.sftp.private-key", privateKey.toUri().toString());
        properties.put("sepa.sftp.private-key-passphrase", PASSPHRASE);
        properties.put("spring.datasource.url", postgres.getJdbcUrl());
        properties.put("spring.datasource.username", postgres.getUsername());
        properties.put("spring.datasource.password", postgres.getPassword());
        application = new SpringApplicationBuilder(PaymentOrderValidatorApplication.class)
                .run(properties.entrySet().stream()
                        .map(property -> "--" + property.getKey() + "=" + property.getValue())
                        .toArray(String[]::new));
        var customer = new SftpTestClient(application.getBean(SessionFactory.class));
        String fileName = "key-auth-" + UUID.randomUUID() + ".xml";

        customer.upload("inbound", fileName, bytes(validPain001(uniqueMessageId())));

        String reportPrefix = fileName.replace(".xml", "") + "-";
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(customer.list("outbound"))
                .anySatisfy(name -> assertThat(name).startsWith(reportPrefix)));
        assertThat(sftp.getLogs()).contains("Accepted publickey for").doesNotContain("Accepted password for");
    }
}
