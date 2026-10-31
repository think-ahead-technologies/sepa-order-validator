// ABOUTME: Creates the SFTP session factory for the customer SFTP channel from SftpProperties.
// ABOUTME: Authenticates only with configured credentials, never with keys found in the runtime user's ~/.ssh.
package dev.thinkahead.sepa.intake;

import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SftpProperties.class)
class SftpConfiguration {

    @Bean
    DefaultSftpSessionFactory sftpSessionFactory(SftpProperties properties) {
        var factory = new DefaultSftpSessionFactory();
        factory.setHost(properties.host());
        factory.setPort(properties.port());
        factory.setUser(properties.username());
        factory.setPassword(properties.password());
        factory.setKnownHostsResource(properties.knownHosts());
        factory.setSshClientConfigurer(client ->
                client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER));
        return factory;
    }
}
