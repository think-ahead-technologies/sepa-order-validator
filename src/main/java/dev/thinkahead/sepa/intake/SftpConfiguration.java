// ABOUTME: Creates the SFTP session factory for the customer SFTP channel from SftpProperties, cached for reuse.
// ABOUTME: Authenticates only with the configured password or private key, never with keys from the user's ~/.ssh.
package dev.thinkahead.sepa.intake;

import org.apache.sshd.client.auth.password.PasswordIdentityProvider;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SftpProperties.class, IntakeProperties.class})
class SftpConfiguration {

    /**
     * Upper bound of open SSH connections to the customer SFTP server; a caller beyond it waits for a free one.
     */
    static final int MAX_SESSIONS = 8;

    @Bean
    DefaultSftpSessionFactory sftpSessionFactory(SftpProperties properties) {
        var factory = new DefaultSftpSessionFactory();
        factory.setHost(properties.host());
        factory.setPort(properties.port());
        factory.setUser(properties.username());
        factory.setKnownHostsResource(properties.knownHosts());
        if (properties.privateKey() != null) {
            factory.setPrivateKey(properties.privateKey());
            factory.setPrivateKeyPassphrase(properties.privateKeyPassphrase());
            // The configured key has replaced the default key provider; only password login is left to disable.
            factory.setSshClientConfigurer(client ->
                    client.setPasswordIdentityProvider(PasswordIdentityProvider.EMPTY_PASSWORDS_PROVIDER));
        } else {
            factory.setPassword(properties.password());
            factory.setSshClientConfigurer(client ->
                    client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER));
        }
        return factory;
    }

    /**
     * Keeps SSH sessions open between operations, so a poll does not pay one SSH handshake per file operation.
     * A cached session is tested before reuse, so a connection the server dropped is replaced transparently.
     */
    @Bean
    @Primary
    CachingSessionFactory<SftpClient.DirEntry> cachingSftpSessionFactory(DefaultSftpSessionFactory sessions) {
        var caching = new CachingSessionFactory<>(sessions, MAX_SESSIONS);
        caching.setTestSession(true);
        return caching;
    }
}
