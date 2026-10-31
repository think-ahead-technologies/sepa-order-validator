// ABOUTME: Test-side SFTP client acting as the bank customer: uploads files and fetches status reports.
// ABOUTME: Talks to the real SFTP container through the application's configured session factory.
package dev.thinkahead.sepa;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;

public final class SftpTestClient {

    private final SessionFactory<SftpClient.DirEntry> sessions;

    public SftpTestClient(SessionFactory<SftpClient.DirEntry> sessions) {
        this.sessions = sessions;
    }

    public void upload(String directory, String fileName, byte[] content) {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            session.write(new ByteArrayInputStream(content), directory + "/" + fileName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<String> list(String directory) {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            return Arrays.stream(session.list(directory))
                    .filter(entry -> entry.getAttributes().isRegularFile())
                    .map(SftpClient.DirEntry::getFilename)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Changes the permission bits of a file over SFTP, e.g. 0 to make it unreadable for the application.
     */
    public void chmod(String directory, String fileName, int permissions) {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            var client = (SftpClient) session.getClientInstance();
            client.setStat(directory + "/" + fileName, new SftpClient.Attributes().perms(permissions));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] download(String directory, String fileName) {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            var out = new ByteArrayOutputStream();
            session.read(directory + "/" + fileName, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
