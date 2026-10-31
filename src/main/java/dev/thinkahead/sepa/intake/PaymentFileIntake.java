// ABOUTME: Accepts a claimed payment file: stores it, schedules its validation and publishes PaymentFileReceived.
// ABOUTME: Idempotent per file id, so a claim processed again after a crash is stored and announced only once.
package dev.thinkahead.sepa.intake;

import dev.thinkahead.sepa.processing.Step;
import dev.thinkahead.sepa.processing.WorkQueue;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentFileIntake {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentFileIntake.class);

    private final PaymentFileRepository repository;
    private final WorkQueue workQueue;
    private final ApplicationEventPublisher events;

    PaymentFileIntake(PaymentFileRepository repository, WorkQueue workQueue, ApplicationEventPublisher events) {
        this.repository = repository;
        this.workQueue = workQueue;
        this.events = events;
    }

    /**
     * Stores the file, schedules its validation and announces it, unless a file with this id was already received.
     *
     * @return true if the file was newly received
     */
    @Transactional
    boolean receive(UUID fileId, Channel channel, String submitter, String fileName, ReceivedContent content) {
        if (!repository.insertIfAbsent(fileId, channel, submitter, fileName, content)) {
            return false;
        }
        workQueue.enqueue(fileId, Step.VALIDATE);
        events.publishEvent(new PaymentFileReceived(fileId, channel, submitter, fileName, content.sha256()));
        LOG.info("Received payment file {} ({} bytes) from {}", fileName, content.size(), submitter);
        return true;
    }
}
