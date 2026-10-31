// ABOUTME: Real infrastructure for integration and end-to-end tests: PostgreSQL and an SFTP server in containers.
// ABOUTME: Each Spring test context gets its own containers so SFTP pollers of cached contexts never compete.
package dev.thinkahead.sepa;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String SFTP_USER = "sepa";
    static final String SFTP_PASSWORD = "sepa-test-password";
    static final int SFTP_PORT = 22;

    private static final String[] HOST_KEYS = {
            "/etc/ssh/ssh_host_ecdsa_key.pub",
            "/etc/ssh/ssh_host_rsa_key.pub"
    };

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17.6-alpine");
    }

    @Bean
    GenericContainer<?> sftp() {
        var image = new ImageFromDockerfile("payment-order-validator-test-sftp", false)
                .withFileFromClasspath("Dockerfile", "sftp/Dockerfile")
                .withBuildArg("SFTP_USER", SFTP_USER)
                .withBuildArg("SFTP_PASSWORD", SFTP_PASSWORD);
        return new GenericContainer<>(image)
                .withExposedPorts(SFTP_PORT)
                .waitingFor(Wait.forLogMessage(".*Server listening on 0\\.0\\.0\\.0 port 22.*", 1));
    }

    @Bean
    DynamicPropertyRegistrar sftpProperties(GenericContainer<?> sftp) {
        return registry -> {
            registry.add("sepa.sftp.host", sftp::getHost);
            registry.add("sepa.sftp.port", () -> sftp.getMappedPort(SFTP_PORT));
            registry.add("sepa.sftp.username", () -> SFTP_USER);
            registry.add("sepa.sftp.password", () -> SFTP_PASSWORD);
            registry.add("sepa.sftp.known-hosts", () -> knownHostsFile(sftp).toUri().toString());
            registry.add("sepa.sftp.poll-interval", () -> "PT0.5S");
        };
    }

    private static Path knownHostsFile(GenericContainer<?> sftp) {
        try {
            var knownHosts = new StringBuilder();
            for (String hostKey : HOST_KEYS) {
                String publicKey = sftp.execInContainer("cat", hostKey).getStdout().trim();
                String[] typeAndKey = publicKey.split(" ");
                knownHosts.append("[").append(sftp.getHost()).append("]:").append(sftp.getMappedPort(SFTP_PORT))
                        .append(' ').append(typeAndKey[0]).append(' ').append(typeAndKey[1]).append('\n');
            }
            Path file = Files.createTempFile("sftp-known-hosts", "");
            file.toFile().deleteOnExit();
            return Files.writeString(file, knownHosts);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
