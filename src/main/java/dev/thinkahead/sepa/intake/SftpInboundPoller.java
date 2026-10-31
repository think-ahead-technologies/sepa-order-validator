// ABOUTME: On the elected leader only, polls SFTP inbound, hands each new file to intake and archives it.
// ABOUTME: Archived names are prefixed with the file id so identical customer file names never collide.
package dev.thinkahead.sepa.intake;

import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SftpInboundPoller {

    private final SftpChannel sftp;
    private final PaymentFileIntake intake;
    private final IntakeLeadership leadership;

    SftpInboundPoller(SftpChannel sftp, PaymentFileIntake intake, IntakeLeadership leadership) {
        this.sftp = sftp;
        this.intake = intake;
        this.leadership = leadership;
    }

    @Scheduled(fixedDelayString = "${sepa.sftp.poll-interval}")
    void poll() {
        if (!leadership.isLeader()) {
            return;
        }
        for (String fileName : sftp.listInbound()) {
            byte[] content = sftp.readInbound(fileName);
            UUID fileId = intake.receive(Channel.SFTP, sftp.submitter(), fileName, content);
            sftp.moveToProcessed(fileName, fileId + "-" + fileName);
        }
    }
}
