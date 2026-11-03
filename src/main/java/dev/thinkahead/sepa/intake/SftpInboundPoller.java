// ABOUTME: On the elected leader only: claims finished SFTP inbound uploads, then receives and archives claimed files.
// ABOUTME: Each file is received in its own span and isolation: a failing file is logged and retried later.
package dev.thinkahead.sepa.intake;

import dev.thinkahead.sepa.LogContext;
import dev.thinkahead.sepa.intake.SftpChannel.RemoteFile;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

@Component
class SftpInboundPoller implements SchedulingConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(SftpInboundPoller.class);

    private final SftpChannel sftp;
    private final PaymentFileIntake intake;
    private final IntakeLeadership leadership;
    private final IntakeProperties properties;
    private final SftpProperties sftpProperties;
    private final ObservationRegistry observations;
    private final FinishedUploads finishedUploads = new FinishedUploads();
    private final FinishedUploads finishedInstantUploads = new FinishedUploads();

    SftpInboundPoller(
            SftpChannel sftp,
            PaymentFileIntake intake,
            IntakeLeadership leadership,
            IntakeProperties properties,
            SftpProperties sftpProperties,
            ObservationRegistry observations) {
        this.sftp = sftp;
        this.intake = intake;
        this.leadership = leadership;
        this.properties = properties;
        this.sftpProperties = sftpProperties;
        this.observations = observations;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::poll, sftpProperties.pollInterval());
    }

    void poll() {
        if (!leadership.isLeader()) {
            finishedUploads.forget();
            finishedInstantUploads.forget();
            return;
        }
        for (String fileName : finishedInstantUploads.finished(sftp.listInstantInbound())) {
            try {
                sftp.claimInstant(fileName, new ClaimedFile(UUID.randomUUID(), fileName, true).claimedName());
            } catch (RuntimeException e) {
                LOG.error("Could not claim SEPA Instant inbound file {}, retrying on the next poll", fileName, e);
            }
        }
        for (String fileName : finishedUploads.finished(sftp.listInbound())) {
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
            try (var ignored = LogContext.scopedToFile(claimed.get().fileId())) {
                try {
                    Observation.createNotStarted("sepa.intake.receive", observations)
                            .highCardinalityKeyValue(
                                    "sepa.file.id", claimed.get().fileId().toString())
                            .observe(() -> receiveAndArchive(claimed.get(), file.size()));
                } catch (RuntimeException e) {
                    LOG.error("Could not take in claimed file {}, retrying on the next poll", file.name(), e);
                }
            }
        }
    }

    private void receiveAndArchive(ClaimedFile claimed, long size) {
        ReceivedContent content = size > properties.maxFileSize().toBytes()
                ? sftp.hashProcessing(claimed.claimedName())
                : sftp.readProcessing(claimed.claimedName());
        intake.receive(
                claimed.fileId(), Channel.SFTP, sftp.submitter(), claimed.originalName(), content, claimed.instant());
        sftp.archive(claimed.claimedName());
    }
}
