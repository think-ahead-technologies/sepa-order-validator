// ABOUTME: Unit tests for SftpProperties validation: connection, credentials and poll interval fail at startup.
// ABOUTME: Exactly one of password or private key must be configured, and the poll interval must be positive.
package dev.thinkahead.sepa.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class SftpPropertiesTest {

    private static final Resource KNOWN_HOSTS = new ByteArrayResource(new byte[0]);
    private static final Resource PRIVATE_KEY = new ByteArrayResource(new byte[0]);

    @Test
    void acceptsPasswordAuthentication() {
        assertThat(properties("sftp.example", 22, "customer", "secret", null, Duration.ofSeconds(5))
                        .password())
                .isEqualTo("secret");
    }

    @Test
    void acceptsKeyAuthentication() {
        assertThat(properties("sftp.example", 22, "customer", null, PRIVATE_KEY, Duration.ofSeconds(5))
                        .privateKey())
                .isSameAs(PRIVATE_KEY);
    }

    @Test
    void leavesTheSepaInstantInboxOptional() {
        var properties = new SftpProperties(
                "sftp.example",
                22,
                "customer",
                "secret",
                null,
                null,
                KNOWN_HOSTS,
                "inbound",
                null,
                "processing",
                "processed",
                "outbound",
                "staging",
                Duration.ofSeconds(5),
                Duration.ofSeconds(30));

        assertThat(properties.instantInboundDirectory()).isNull();
    }

    @Test
    void requiresKnownHosts() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SftpProperties(
                        "sftp.example",
                        22,
                        "customer",
                        "secret",
                        null,
                        null,
                        null,
                        "inbound",
                        "inbound-instant",
                        "processing",
                        "processed",
                        "outbound",
                        "staging",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30)))
                .withMessageContaining("sepa.sftp.known-hosts");
    }

    @Test
    void requiresHost() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(" ", 22, "customer", "secret", null, Duration.ofSeconds(5)))
                .withMessageContaining("sepa.sftp.host");
    }

    @Test
    void requiresAValidPort() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties("sftp.example", 0, "customer", "secret", null, Duration.ofSeconds(5)))
                .withMessageContaining("sepa.sftp.port");
    }

    @Test
    void requiresUsername() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties("sftp.example", 22, null, "secret", null, Duration.ofSeconds(5)))
                .withMessageContaining("sepa.sftp.username");
    }

    @Test
    void requiresPasswordOrPrivateKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties("sftp.example", 22, "customer", null, null, Duration.ofSeconds(5)))
                .withMessageContaining("sepa.sftp.password")
                .withMessageContaining("sepa.sftp.private-key");
    }

    @Test
    void rejectsPasswordAndPrivateKeyTogether() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> properties("sftp.example", 22, "customer", "secret", PRIVATE_KEY, Duration.ofSeconds(5)))
                .withMessageContaining("sepa.sftp.password")
                .withMessageContaining("sepa.sftp.private-key");
    }

    @Test
    void requiresAStagingDirectory() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SftpProperties(
                        "sftp.example",
                        22,
                        "customer",
                        "secret",
                        null,
                        null,
                        KNOWN_HOSTS,
                        "inbound",
                        "inbound-instant",
                        "processing",
                        "processed",
                        "outbound",
                        " ",
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30)))
                .withMessageContaining("sepa.sftp.staging-directory");
    }

    @Test
    void requiresAPositiveTimeout() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SftpProperties(
                        "sftp.example",
                        22,
                        "customer",
                        "secret",
                        null,
                        null,
                        KNOWN_HOSTS,
                        "inbound",
                        "inbound-instant",
                        "processing",
                        "processed",
                        "outbound",
                        "staging",
                        Duration.ofSeconds(5),
                        Duration.ZERO))
                .withMessageContaining("sepa.sftp.timeout");
    }

    @Test
    void requiresAPositivePollInterval() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties("sftp.example", 22, "customer", "secret", null, Duration.ZERO))
                .withMessageContaining("sepa.sftp.poll-interval");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties("sftp.example", 22, "customer", "secret", null, null))
                .withMessageContaining("sepa.sftp.poll-interval");
    }

    private static SftpProperties properties(
            String host, int port, String username, String password, Resource privateKey, Duration pollInterval) {
        return new SftpProperties(
                host,
                port,
                username,
                password,
                privateKey,
                null,
                KNOWN_HOSTS,
                "inbound",
                "inbound-instant",
                "processing",
                "processed",
                "outbound",
                "staging",
                pollInterval,
                Duration.ofSeconds(30));
    }
}
