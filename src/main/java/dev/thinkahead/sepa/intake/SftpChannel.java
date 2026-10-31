// ABOUTME: File operations on the customer SFTP server: list inbound, claim, read and archive claims, write outbound.
// ABOUTME: Claiming is an atomic rename, so of several competing instances exactly one gets each file.
package dev.thinkahead.sepa.intake;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
    List<RemoteFile> listInbound() {
        return xmlFiles(properties.inboundDirectory());
    }

    /**
     * Customer files waiting in the SEPA Instant inbox; empty if that inbox is not configured.
     */
    List<RemoteFile> listInstantInbound() {
        if (!StringUtils.hasText(properties.instantInboundDirectory())) {
            return List.of();
        }
        return xmlFiles(properties.instantInboundDirectory());
    }

    private List<RemoteFile> xmlFiles(String directory) {
        return list(directory).stream()
                .filter(file -> file.name().toLowerCase(Locale.ROOT).endsWith(".xml"))
                .toList();
    }

    /**
     * Atomically moves an inbound file to the processing directory under its claimed name.
     *
     * @return false if the file is no longer in inbound because another instance claimed it first
     */
    boolean claim(String fileName, String claimedName) {
        return claimFrom(properties.inboundDirectory(), fileName, claimedName);
    }

    /**
     * Atomically moves a file from the SEPA Instant inbox to the processing directory under its claimed name.
     *
     * @return false if the file is no longer in the inbox because another instance claimed it first
     */
    boolean claimInstant(String fileName, String claimedName) {
        return claimFrom(properties.instantInboundDirectory(), fileName, claimedName);
    }

    private boolean claimFrom(String inbox, String fileName, String claimedName) {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            return renameIfPresent(session, inbox + "/" + fileName,
                    properties.processingDirectory() + "/" + claimedName);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot claim SFTP inbound file " + fileName, e);
        }
    }

    List<RemoteFile> listProcessing() {
        return list(properties.processingDirectory());
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

    /**
     * A regular file as listed by the server; modifiedAt has the server's resolution, often whole seconds,
     * and is null if the server does not report it.
     */
    record RemoteFile(String name, long size, Instant modifiedAt) {
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

    /**
     * Writes to the staging directory, then renames into outbound, so the customer never sees a partial file.
     * An existing file of the same name in outbound is replaced.
     */
    void writeOutbound(String fileName, byte[] content) {
        String staged = properties.stagingDirectory() + "/" + fileName;
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            session.write(new ByteArrayInputStream(content), staged);
            session.rename(staged, properties.outboundDirectory() + "/" + fileName);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write SFTP outbound file " + fileName, e);
        }
    }

    private List<RemoteFile> list(String directory) {
        try (Session<SftpClient.DirEntry> session = sessions.getSession()) {
            return Arrays.stream(session.list(directory))
                    .filter(entry -> entry.getAttributes().isRegularFile())
                    .map(SftpChannel::remoteFile)
                    .sorted(Comparator.comparing(RemoteFile::name))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list SFTP directory " + directory, e);
        }
    }

    private static RemoteFile remoteFile(SftpClient.DirEntry entry) {
        FileTime modified = entry.getAttributes().getModifyTime();
        return new RemoteFile(entry.getFilename(), entry.getAttributes().getSize(),
                modified == null ? null : modified.toInstant());
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
