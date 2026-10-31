// ABOUTME: Real infrastructure for integration and end-to-end tests: PostgreSQL and an SFTP server in containers.
// ABOUTME: Each Spring test context gets its own containers so SFTP pollers of cached contexts never compete.
package dev.thinkahead.sepa;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.util.function.SingletonSupplier;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String SFTP_USER = "sepa";
    static final String SFTP_PASSWORD = "sepa-test-password";
    static final int SFTP_PORT = 22;

    private static final List<String> APPLICATION_PROPERTY_NAMES = List.of(
            "sepa.sftp.host", "sepa.sftp.port", "sepa.sftp.username", "sepa.sftp.password",
            "sepa.sftp.known-hosts", "sepa.sftp.poll-interval",
            "sepa.leader-election.lock-time-to-live", "sepa.leader-election.heartbeat", "sepa.intake.max-file-size",
            "sepa.intake.reply-check-interval", "server.port", "sepa.processing.deliver.poll-interval",
            "sepa.processing.deliver.initial-backoff", "sepa.processing.deliver.max-backoff",
            "sepa.processing.deliver.lease");

    private static final String[] HOST_KEYS = {
            "/etc/ssh/ssh_host_ecdsa_key.pub",
            "/etc/ssh/ssh_host_rsa_key.pub"
    };

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return newPostgresContainer();
    }

    @Bean
    GenericContainer<?> sftp() {
        return newSftpContainer();
    }

    @Bean
    DynamicPropertyRegistrar applicationPropertyRegistrar(GenericContainer<?> sftp) {
        return registry -> {
            Supplier<Map<String, Object>> properties = SingletonSupplier.of(() -> applicationProperties(sftp));
            for (String name : APPLICATION_PROPERTY_NAMES) {
                registry.add(name, () -> properties.get().get(name));
            }
        };
    }

    public static PostgreSQLContainer newPostgresContainer() {
        return new PostgreSQLContainer("postgres:17.6-alpine");
    }

    public static GenericContainer<?> newSftpContainer() {
        var image = new ImageFromDockerfile("payment-order-validator-test-sftp", false)
                .withFileFromClasspath("Dockerfile", "sftp/Dockerfile")
                .withBuildArg("SFTP_USER", SFTP_USER)
                .withBuildArg("SFTP_PASSWORD", SFTP_PASSWORD);
        return new GenericContainer<>(image)
                .withExposedPorts(SFTP_PORT)
                .waitingFor(Wait.forLogMessage(".*Server listening on 0\\.0\\.0\\.0 port 22.*", 1));
    }

    /**
     * Application properties connecting to the given started SFTP container, with fast polling and leader election.
     */
    public static Map<String, Object> applicationProperties(GenericContainer<?> sftp) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("sepa.sftp.host", sftp.getHost());
        properties.put("sepa.sftp.port", sftp.getMappedPort(SFTP_PORT));
        properties.put("sepa.sftp.username", SFTP_USER);
        properties.put("sepa.sftp.password", SFTP_PASSWORD);
        properties.put("sepa.sftp.known-hosts", knownHostsFile(sftp).toUri().toString());
        properties.put("sepa.sftp.poll-interval", "PT0.5S");
        properties.put("sepa.leader-election.lock-time-to-live", "PT3S");
        properties.put("sepa.leader-election.heartbeat", "PT0.5S");
        properties.put("sepa.intake.max-file-size", "1MB");
        properties.put("sepa.intake.reply-check-interval", "PT1H");
        properties.put("server.port", 0);
        properties.put("sepa.processing.deliver.poll-interval", "PT0.2S");
        properties.put("sepa.processing.deliver.initial-backoff", "PT0.5S");
        properties.put("sepa.processing.deliver.max-backoff", "PT1S");
        properties.put("sepa.processing.deliver.lease", "PT10S");
        return properties;
    }

    /**
     * A customer client with its own password-authenticated connection, independent of any application instance.
     */
    public static SftpTestClient newCustomerClient(GenericContainer<?> sftp) {
        var sessions = new DefaultSftpSessionFactory();
        sessions.setHost(sftp.getHost());
        sessions.setPort(sftp.getMappedPort(SFTP_PORT));
        sessions.setUser(SFTP_USER);
        sessions.setPassword(SFTP_PASSWORD);
        sessions.setKnownHostsResource(new FileSystemResource(knownHostsFile(sftp)));
        sessions.setSshClientConfigurer(client ->
                client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER));
        return new SftpTestClient(sessions);
    }

    /**
     * Generates a passphrase-protected ECDSA client key in the SFTP container, optionally authorizes it for the
     * customer account, and returns the private key as a local file in OpenSSH format.
     */
    public static Path newClientKey(GenericContainer<?> sftp, String passphrase, boolean authorize) {
        String keyFile = "/tmp/client-key-" + UUID.randomUUID();
        String sshDirectory = "/home/" + SFTP_USER + "/.ssh";
        try {
            exec(sftp, "ssh-keygen", "-q", "-t", "ecdsa", "-b", "256", "-N", passphrase, "-f", keyFile);
            if (authorize) {
                exec(sftp, "sh", "-c", "mkdir -p " + sshDirectory
                        + " && cat " + keyFile + ".pub >> " + sshDirectory + "/authorized_keys"
                        + " && chown -R " + SFTP_USER + ":" + SFTP_USER + " " + sshDirectory
                        + " && chmod 700 " + sshDirectory + " && chmod 600 " + sshDirectory + "/authorized_keys");
            }
            Path file = Files.createTempFile("sftp-client-key", "");
            file.toFile().deleteOnExit();
            return Files.writeString(file, exec(sftp, "cat", keyFile));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String exec(GenericContainer<?> container, String... command) {
        try {
            var result = container.execInContainer(command);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("Command " + String.join(" ", command) + " failed: "
                        + result.getStderr());
            }
            return result.getStdout();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
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
