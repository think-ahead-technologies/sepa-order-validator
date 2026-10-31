// ABOUTME: File operations on the customer SFTP server: list and read inbound, archive, write outbound.
// ABOUTME: Only *.xml files are picked up, so customers can upload under a temporary name and rename.
package dev.thinkahead.sepa.intake;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.stereotype.Component;

@Component
class SftpChannel {

    private final SessionFactory<SftpClient.DirEntry> sessions;
    private final SftpProperties properties;

    SftpChannel(SessionFactory<SftpClient.DirEntry> sessions, SftpProperties properties) {
        this.sessions = sessions;
        this.properties = properties;
    }

    List<String> listInbound() {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            return Arrays.stream(session.list(properties.inboundDirectory()))
                    .filter(entry -> entry.getAttributes().isRegularFile())
                    .map(SftpClient.DirEntry::getFilename)
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".xml"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list SFTP inbound directory", e);
        }
    }

    byte[] readInbound(String fileName) {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            var out = new ByteArrayOutputStream();
            session.read(properties.inboundDirectory() + "/" + fileName, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read SFTP inbound file " + fileName, e);
        }
    }

    void moveToProcessed(String fileName, String archivedName) {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            session.rename(properties.inboundDirectory() + "/" + fileName,
                    properties.processedDirectory() + "/" + archivedName);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot archive SFTP inbound file " + fileName, e);
        }
    }

    void writeOutbound(String fileName, byte[] content) {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            session.write(new ByteArrayInputStream(content), properties.outboundDirectory() + "/" + fileName);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write SFTP outbound file " + fileName, e);
        }
    }
}
