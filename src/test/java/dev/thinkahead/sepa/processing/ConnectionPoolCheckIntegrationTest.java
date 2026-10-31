// ABOUTME: Integration test that the application refuses to start with a connection pool too small for its workers.
// ABOUTME: Starts the real application against a PostgreSQL container with a deliberately small pool.
package dev.thinkahead.sepa.processing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.thinkahead.sepa.PaymentOrderValidatorApplication;
import dev.thinkahead.sepa.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

class ConnectionPoolCheckIntegrationTest {

    private final PostgreSQLContainer postgres = TestcontainersConfiguration.newPostgresContainer();

    @BeforeEach
    void startDatabase() {
        postgres.start();
    }

    @AfterEach
    void stopDatabase() {
        postgres.stop();
    }

    @Test
    void refusesToStartWithAPoolSmallerThanTheWorkersNeed() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(PaymentOrderValidatorApplication.class).run(
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.datasource.hikari.maximum-pool-size=5",
                "--server.port=0",
                "--sepa.sftp.host=sftp.invalid",
                "--sepa.sftp.username=customer",
                "--sepa.sftp.password=unused",
                "--sepa.sftp.known-hosts=file:/dev/null"))
                .rootCause()
                .hasMessageContaining("spring.datasource.hikari.maximum-pool-size")
                .hasMessageContaining("is 5");
    }
}
