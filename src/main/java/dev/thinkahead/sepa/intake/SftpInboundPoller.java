// ABOUTME: On the elected leader only: claims new SFTP inbound files, then receives and archives every claimed file.
// ABOUTME: Each file is handled in isolation: a failing file is logged and retried without blocking the others.
package dev.thinkahead.sepa.intake;

import dev.thinkahead.sepa.intake.SftpChannel.RemoteFile;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SftpInboundPoller {

    private static final Logger LOG = LoggerFactory.getLogger(SftpInboundPoller.class);

    private final SftpChannel sftp;
    private final PaymentFileIntake intake;
    private final IntakeLeadership leadership;
    private final IntakeProperties properties;

    SftpInboundPoller(SftpChannel sftp, PaymentFileIntake intake, IntakeLeadership leadership,
            IntakeProperties properties) {
        this.sftp = sftp;
        this.intake = intake;
        this.leadership = leadership;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${sepa.sftp.poll-interval}")
    void poll() {
        if (!leadership.isLeader()) {
            return;
        }
        for (String fileName : sftp.listInbound()) {
            try {
                sftp.claim(fileName, new ClaimedFile(UUID.randomUUID(), fileName).claimedName());
            } catch (RuntimeException e) {
                LOG.error("Could not claim inbound file {}, retrying on the next poll", fileName, e);
            }
        }
        for (RemoteFile file : sftp.listProcessing()) {
            Optional<ClaimedFile> claimed = ClaimedFile.parse(file.name());
            if (claimed.isEmpty()) {
                LOG.warn("Ignoring {} in the SFTP processing directory: not a claimed file name", file.name());
                continue;
            }
            try {
                receiveAndArchive(claimed.get(), file.size());
            } catch (RuntimeException e) {
                LOG.error("Could not take in claimed file {}, retrying on the next poll", file.name(), e);
            }
        }
    }

    private void receiveAndArchive(ClaimedFile claimed, long size) {
        ReceivedContent content = size > properties.maxFileSize().toBytes()
                ? sftp.hashProcessing(claimed.claimedName())
                : sftp.readProcessing(claimed.claimedName());
        intake.receive(claimed.fileId(), Channel.SFTP, sftp.submitter(), claimed.originalName(), content);
        sftp.archive(claimed.claimedName());
    }
}
