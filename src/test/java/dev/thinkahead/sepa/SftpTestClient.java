// ABOUTME: Test-side SFTP client acting as the bank customer: uploads files and fetches status reports.
// ABOUTME: Talks to the real SFTP container through the application's configured session factory.
package dev.thinkahead.sepa;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
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

    /**
     * Uploads like a customer on a slow line: in chunks under the final name, pausing after each chunk.
     * Runs the given check after every chunk while the file is still incomplete.
     */
    public void uploadSlowly(
            String directory, String fileName, byte[] content, int chunks, Duration pause, Runnable whileIncomplete) {
        int chunkSize = Math.ceilDiv(content.length, chunks);
        try (Session<SftpClient.DirEntry> session = sessions.getSession();
                OutputStream out = ((SftpClient) session.getClientInstance()).write(directory + "/" + fileName)) {
            for (int offset = 0; offset < content.length; offset += chunkSize) {
                out.write(content, offset, Math.min(chunkSize, content.length - offset));
                out.flush();
                Thread.sleep(pause);
                if (offset + chunkSize < content.length) {
                    whileIncomplete.run();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
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
