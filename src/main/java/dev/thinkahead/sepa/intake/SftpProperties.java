// ABOUTME: Connection and directory settings for the customer SFTP channel (prefix "sepa.sftp"), checked at startup.
// ABOUTME: knownHosts is mandatory: the server's host key is always verified, there is no switch to skip it.
package dev.thinkahead.sepa.intake;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

/**
 * @param password             password of the SFTP account; exactly one of password and privateKey is set
 * @param privateKey           private key in OpenSSH or PEM format for public key authentication
 * @param privateKeyPassphrase passphrase of the private key, if it is encrypted
 * @param stagingDirectory     where reports are written before the rename into outbound; same file system as outbound
 * @param pollInterval         pause between the end of one inbound poll and the start of the next
 */
@ConfigurationProperties("sepa.sftp")
record SftpProperties(
        String host,
        int port,
        String username,
        String password,
        Resource privateKey,
        String privateKeyPassphrase,
        Resource knownHosts,
        String inboundDirectory,
        String processingDirectory,
        String processedDirectory,
        String outboundDirectory,
        String stagingDirectory,
        Duration pollInterval) {

    SftpProperties {
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("sepa.sftp.host is required");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("sepa.sftp.port must be between 1 and 65535, was " + port);
        }
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("sepa.sftp.username is required");
        }
        if (StringUtils.hasText(password) == (privateKey != null)) {
            throw new IllegalArgumentException(
                    "Exactly one of sepa.sftp.password and sepa.sftp.private-key must be configured");
        }
        if (knownHosts == null) {
            throw new IllegalArgumentException("sepa.sftp.known-hosts must point to a known_hosts file");
        }
        if (!StringUtils.hasText(stagingDirectory)) {
            throw new IllegalArgumentException("sepa.sftp.staging-directory is required");
        }
        if (pollInterval == null || !pollInterval.isPositive()) {
            throw new IllegalArgumentException("sepa.sftp.poll-interval must be positive, was " + pollInterval);
        }
    }
}
