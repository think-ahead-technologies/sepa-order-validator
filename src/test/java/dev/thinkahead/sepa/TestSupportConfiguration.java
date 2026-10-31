// ABOUTME: Registers test helpers (customer SFTP client, event capture) as beans for Spring-based tests.
// ABOUTME: Imported together with TestcontainersConfiguration by integration and end-to-end tests.
package dev.thinkahead.sepa;

import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.file.remote.session.SessionFactory;

@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfiguration {

    @Bean
    SftpTestClient sftpTestClient(SessionFactory<SftpClient.DirEntry> sessions) {
        return new SftpTestClient(sessions);
    }

    @Bean
    CapturedEvents capturedEvents() {
        return new CapturedEvents();
    }
}
