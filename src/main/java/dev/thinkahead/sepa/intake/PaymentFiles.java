// ABOUTME: Public facade of the intake module for other modules: file content, status updates, report writing.
// ABOUTME: Status reports are delivered through the channel the payment file arrived on (ADR-0013).
package dev.thinkahead.sepa.intake;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PaymentFiles {

    private final PaymentFileRepository repository;
    private final SftpChannel sftp;

    PaymentFiles(PaymentFileRepository repository, SftpChannel sftp) {
        this.repository = repository;
        this.sftp = sftp;
    }

    /**
     * @return the file's bytes, or empty if the file exceeded the maximum size and was not stored
     */
    public Optional<byte[]> content(UUID fileId) {
        return repository.content(fileId);
    }

    /**
     * The authenticated identity that submitted the file, e.g. {@code sftp:<account>}.
     */
    public String submitter(UUID fileId) {
        return repository.submitter(fileId);
    }

    /**
     * True while the file has been received but no validation outcome is recorded yet.
     */
    public boolean isAwaitingValidation(UUID fileId) {
        return repository.status(fileId) == FileStatus.RECEIVED;
    }

    /**
     * Records the outcome of the first validation of the file.
     *
     * @return false if an outcome was already recorded, e.g. by a concurrent delivery of the same event
     */
    public boolean recordValidationOutcome(UUID fileId, boolean accepted) {
        return repository.updateStatus(fileId, FileStatus.RECEIVED,
                accepted ? FileStatus.ACCEPTED : FileStatus.REJECTED);
    }

    /**
     * Writes the report to the customer through the file's channel; writing it again replaces it under the same name.
     * Channel I/O only: call it outside a transaction and record the reply with {@link #markReplied(UUID)}.
     */
    public void writeStatusReport(UUID fileId, byte[] report) {
        StoredPaymentFile file = repository.find(fileId);
        switch (file.channel()) {
            case SFTP -> sftp.writeOutbound(statusReportName(file), report);
            default -> throw new IllegalStateException(
                    "No status report delivery for channel " + file.channel() + " of file " + fileId);
        }
    }

    /**
     * Records that the customer was sent a status report for the file, which ends the overdue-reply alert.
     */
    public void markReplied(UUID fileId) {
        repository.markReplied(fileId);
    }

    private static String statusReportName(StoredPaymentFile file) {
        String baseName = file.fileName().replaceFirst("(?i)\\.xml$", "");
        return baseName + "-" + file.id() + ".pain002.xml";
    }
}
