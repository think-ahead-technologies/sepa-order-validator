// ABOUTME: Accepts a claimed file or a message split from a container: stores it, schedules VALIDATE, announces it.
// ABOUTME: Idempotent per file id, so a claim processed again after a crash is stored and announced only once.
package dev.thinkahead.sepa.intake;

import dev.thinkahead.sepa.audit.AuditTrail;
import dev.thinkahead.sepa.processing.Priority;
import dev.thinkahead.sepa.processing.Step;
import dev.thinkahead.sepa.processing.WorkQueue;
import java.nio.charset.StandardCharsets;
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
    private final AuditTrail auditTrail;
    private final ApplicationEventPublisher events;

    PaymentFileIntake(PaymentFileRepository repository, WorkQueue workQueue, AuditTrail auditTrail,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.workQueue = workQueue;
        this.auditTrail = auditTrail;
        this.events = events;
    }

    /**
     * Stores the file, schedules its validation, audits and announces it, unless a file with this id was already
     * received.
     *
     * @return true if the file was newly received
     */
    @Transactional
    boolean receive(UUID fileId, Channel channel, String submitter, String fileName, ReceivedContent content) {
        return receive(fileId, channel, submitter, fileName, content, false);
    }

    /**
     * As {@link #receive(UUID, Channel, String, String, ReceivedContent)}; a file from the SEPA Instant inbox is
     * marked as such and processed with priority.
     */
    @Transactional
    boolean receive(UUID fileId, Channel channel, String submitter, String fileName, ReceivedContent content,
            boolean instantOrder) {
        if (!repository.insertIfAbsent(fileId, channel, submitter, fileName, content, instantOrder)) {
            return false;
        }
        announce(fileId, channel, submitter, fileName, content, instantOrder);
        return true;
    }

    /**
     * Stores the message at the given position (from 1) of a container as a payment file of its own, on the
     * container's channel, submitter and inbox, named after the container. Its id follows from the container and
     * position, so storing the same message again finds the existing file.
     *
     * @return the id of the message's payment file
     */
    @Transactional
    UUID receiveFromContainer(UUID containerFileId, int index, byte[] message) {
        UUID fileId = UUID.nameUUIDFromBytes(("container:" + containerFileId + ":" + index)
                .getBytes(StandardCharsets.UTF_8));
        StoredPaymentFile container = repository.find(containerFileId);
        String submitter = repository.submitter(containerFileId);
        String fileName = container.fileName().replaceFirst("(?i)\\.xml$", "") + "-msg" + index + ".xml";
        ReceivedContent content = ReceivedContent.of(message);
        boolean instantOrder = repository.submission(containerFileId).instantOrder();
        if (repository.insertIfAbsent(fileId, container.channel(), submitter, fileName, content, instantOrder,
                containerFileId, index)) {
            announce(fileId, container.channel(), submitter, fileName, content, instantOrder);
        }
        return fileId;
    }

    private void announce(UUID fileId, Channel channel, String submitter, String fileName, ReceivedContent content,
            boolean instantOrder) {
        workQueue.enqueue(fileId, Step.VALIDATE, instantOrder ? Priority.HIGH : Priority.NORMAL);
        var received = new PaymentFileReceived(fileId, channel, submitter, fileName, content.sha256());
        auditTrail.record(fileId, received);
        events.publishEvent(received);
        LOG.info("Received payment file {} ({} bytes) from {}", fileName, content.size(), submitter);
    }
}
