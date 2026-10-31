// ABOUTME: Public facade of the intake module for other modules: file content, status updates, report delivery.
// ABOUTME: Status reports are delivered through the channel the payment file arrived on (ADR-0013).
package dev.thinkahead.sepa.intake;

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

    public byte[] content(UUID fileId) {
        return repository.content(fileId);
    }

    public void recordValidationOutcome(UUID fileId, boolean accepted) {
        repository.updateStatus(fileId, accepted ? FileStatus.ACCEPTED : FileStatus.REJECTED);
    }

    public void deliverStatusReport(UUID fileId, byte[] report) {
        StoredPaymentFile file = repository.find(fileId);
        switch (file.channel()) {
            case SFTP -> sftp.writeOutbound(statusReportName(file), report);
        }
    }

    private static String statusReportName(StoredPaymentFile file) {
        String baseName = file.fileName().replaceFirst("(?i)\\.xml$", "");
        return baseName + "-" + file.id() + ".pain002.xml";
    }
}
