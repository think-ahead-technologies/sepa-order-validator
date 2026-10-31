// ABOUTME: Connection and directory settings for the customer SFTP channel (prefix "sepa.sftp").
// ABOUTME: knownHosts is mandatory: the server's host key is always verified, there is no switch to skip it.
package dev.thinkahead.sepa.intake;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("sepa.sftp")
record SftpProperties(
        String host,
        int port,
        String username,
        String password,
        Resource knownHosts,
        String inboundDirectory,
        String processingDirectory,
        String processedDirectory,
        String outboundDirectory,
        Duration pollInterval) {

    SftpProperties {
        if (knownHosts == null) {
            throw new IllegalArgumentException("sepa.sftp.known-hosts must point to a known_hosts file");
        }
    }
}
