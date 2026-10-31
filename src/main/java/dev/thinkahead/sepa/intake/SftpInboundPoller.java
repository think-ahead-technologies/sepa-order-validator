// ABOUTME: Polls the SFTP inbound directory, hands each new file to intake and archives it afterwards.
// ABOUTME: Archived names are prefixed with the file id so identical customer file names never collide.
package dev.thinkahead.sepa.intake;

import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SftpInboundPoller {

    private final SftpChannel sftp;
    private final PaymentFileIntake intake;

    SftpInboundPoller(SftpChannel sftp, PaymentFileIntake intake) {
        this.sftp = sftp;
        this.intake = intake;
    }

    @Scheduled(fixedDelayString = "${sepa.sftp.poll-interval}")
    void poll() {
        for (String fileName : sftp.listInbound()) {
            byte[] content = sftp.readInbound(fileName);
            UUID fileId = intake.receive(Channel.SFTP, fileName, content);
            sftp.moveToProcessed(fileName, fileId + "-" + fileName);
        }
    }
}
