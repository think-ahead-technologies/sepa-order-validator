// ABOUTME: End-to-end test that stopping the application, as on every pod stop, logs no ERROR.
// ABOUTME: A false ERROR on shutdown would page operations on every deploy and hide real ones.
package dev.thinkahead.sepa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@ExtendWith(OutputCaptureExtension.class)
class CleanShutdownEndToEndTest {

    private final PostgreSQLContainer postgres = TestcontainersConfiguration.newPostgresContainer();
    private final GenericContainer<?> sftp = TestcontainersConfiguration.newSftpContainer();

    @BeforeEach
    void startInfrastructure() {
        postgres.start();
        sftp.start();
    }

    @AfterEach
    void stopInfrastructure() {
        sftp.stop();
        postgres.stop();
    }

    @Test
    void stoppingTheApplicationLogsNoError(CapturedOutput output) {
        Map<String, Object> properties = new HashMap<>(TestcontainersConfiguration.applicationProperties(sftp));
        properties.put("spring.datasource.url", postgres.getJdbcUrl());
        properties.put("spring.datasource.username", postgres.getUsername());
        properties.put("spring.datasource.password", postgres.getPassword());

        new SpringApplicationBuilder(PaymentOrderValidatorApplication.class)
                .run(properties.entrySet().stream()
                        .map(property -> "--" + property.getKey() + "=" + property.getValue())
                        .toArray(String[]::new))
                .close();

        assertThat(LogLines.of(output)).as("log lines").isNotEmpty()
                .noneSatisfy(line -> assertThat(line.path("log").path("level").asString()).isEqualTo("ERROR"));
    }
}
