// ABOUTME: File operations on the customer SFTP server: list inbound, claim, read and archive claims, write outbound.
// ABOUTME: Claiming is an atomic rename, so of several competing instances exactly one gets each file.
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

    /**
     * Identity of whoever submits files on this channel: the authenticated SFTP account.
     */
    String submitter() {
        return "sftp:" + properties.username();
    }

    /**
     * Customer files waiting in inbound. Only *.xml files count, so customers can upload under a temporary name.
     */
    List<String> listInbound() {
        return list(properties.inboundDirectory()).stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".xml"))
                .toList();
    }

    /**
     * Atomically moves an inbound file to the processing directory under its claimed name.
     *
     * @return false if the file is no longer in inbound because another instance claimed it first
     */
    boolean claim(String fileName, String claimedName) {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            return renameIfPresent(session, properties.inboundDirectory() + "/" + fileName,
                    properties.processingDirectory() + "/" + claimedName);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot claim SFTP inbound file " + fileName, e);
        }
    }

    List<RemoteFile> listProcessing() {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            return Arrays.stream(session.list(properties.processingDirectory()))
                    .filter(entry -> entry.getAttributes().isRegularFile())
                    .map(entry -> new RemoteFile(entry.getFilename(), entry.getAttributes().getSize()))
                    .sorted(java.util.Comparator.comparing(RemoteFile::name))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list SFTP processing directory", e);
        }
    }

    /**
     * Reads a claimed file completely; only for files within the maximum file size.
     */
    ReceivedContent readProcessing(String claimedName) {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            var out = new ByteArrayOutputStream();
            session.read(properties.processingDirectory() + "/" + claimedName, out);
            return ReceivedContent.of(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read SFTP processing file " + claimedName, e);
        }
    }

    /**
     * Streams a claimed file through SHA-256 without keeping it, for files above the maximum file size.
     */
    ReceivedContent hashProcessing(String claimedName) {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            ReceivedContent hashed;
            try (var in = session.readRaw(properties.processingDirectory() + "/" + claimedName)) {
                hashed = ReceivedContent.oversized(in);
            }
            session.finalizeRaw();
            return hashed;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot hash SFTP processing file " + claimedName, e);
        }
    }

    record RemoteFile(String name, long size) {
    }

    /**
     * Moves a claimed file to the processed directory; a file another instance already archived is left alone.
     */
    void archive(String claimedName) {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            renameIfPresent(session, properties.processingDirectory() + "/" + claimedName,
                    properties.processedDirectory() + "/" + claimedName);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot archive SFTP processing file " + claimedName, e);
        }
    }

    void writeOutbound(String fileName, byte[] content) {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            session.write(new ByteArrayInputStream(content), properties.outboundDirectory() + "/" + fileName);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write SFTP outbound file " + fileName, e);
        }
    }

    private List<String> list(String directory) {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            return Arrays.stream(session.list(directory))
                    .filter(entry -> entry.getAttributes().isRegularFile())
                    .map(SftpClient.DirEntry::getFilename)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list SFTP directory " + directory, e);
        }
    }

    private static boolean renameIfPresent(Session<SftpClient.DirEntry> session, String from, String to)
            throws IOException {
        try {
            session.rename(from, to);
            return true;
        } catch (IOException e) {
            if (!session.exists(from)) {
                return false;
            }
            throw e;
        }
    }
}
