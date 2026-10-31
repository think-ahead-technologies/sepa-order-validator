// ABOUTME: Integration tests for SFTP public key authentication against a real SFTP container.
// ABOUTME: A passphrase-protected key authorized for the account logs in; an unauthorized key is refused.
package dev.thinkahead.sepa.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

import dev.thinkahead.sepa.TestcontainersConfiguration;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.UrlResource;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.testcontainers.containers.GenericContainer;

class SftpKeyAuthenticationIntegrationTest {

    private static final String PASSPHRASE = "key-passphrase";

    private static final GenericContainer<?> SFTP = TestcontainersConfiguration.newSftpContainer();

    @BeforeAll
    static void startSftp() {
        SFTP.start();
    }

    @AfterAll
    static void stopSftp() {
        SFTP.stop();
    }

    @Test
    void logsInWithAnAuthorizedPrivateKey() throws Exception {
        Path key = TestcontainersConfiguration.newClientKey(SFTP, PASSPHRASE, true);

        DefaultSftpSessionFactory sessions = sessionFactory(key);
        try (var session = sessions.getSession()) {
            assertThat(session.exists("inbound")).isTrue();
        } finally {
            sessions.destroy();
        }
    }

    @Test
    void isRefusedWithAPrivateKeyTheAccountDoesNotAuthorize() throws Exception {
        Path key = TestcontainersConfiguration.newClientKey(SFTP, PASSPHRASE, false);

        DefaultSftpSessionFactory sessions = sessionFactory(key);
        try {
            assertThatException().isThrownBy(sessions::getSession);
        } finally {
            sessions.destroy();
        }
    }

    private static DefaultSftpSessionFactory sessionFactory(Path privateKey) throws Exception {
        Map<String, Object> connection = TestcontainersConfiguration.applicationProperties(SFTP);
        var properties = new SftpProperties(
                (String) connection.get("sepa.sftp.host"),
                (Integer) connection.get("sepa.sftp.port"),
                (String) connection.get("sepa.sftp.username"),
                null,
                new FileSystemResource(privateKey),
                PASSPHRASE,
                new UrlResource((String) connection.get("sepa.sftp.known-hosts")),
                "inbound", "processing", "processed", "outbound",
                Duration.ofSeconds(5));
        return new SftpConfiguration().sftpSessionFactory(properties);
    }
}
