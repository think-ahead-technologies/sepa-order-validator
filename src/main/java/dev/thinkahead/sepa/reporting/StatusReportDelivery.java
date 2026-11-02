// ABOUTME: The DELIVER processing step: sends a file's stored pain.002 to the customer, then records the delivery.
// ABOUTME: Always sends the stored bytes under the same name, so a repeated delivery re-sends the identical report.
package dev.thinkahead.sepa.reporting;

import dev.thinkahead.sepa.audit.AuditTrail;
import dev.thinkahead.sepa.intake.PaymentFiles;
import dev.thinkahead.sepa.processing.Completion;
import dev.thinkahead.sepa.processing.ProcessingStep;
import dev.thinkahead.sepa.processing.Step;
import dev.thinkahead.sepa.reporting.StatusReportRepository.StoredStatusReport;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class StatusReportDelivery implements ProcessingStep {

    private static final Logger LOG = LoggerFactory.getLogger(StatusReportDelivery.class);

    private final StatusReportRepository repository;
    private final PaymentFiles paymentFiles;
    private final AuditTrail auditTrail;
    private final ApplicationEventPublisher events;

    StatusReportDelivery(
            StatusReportRepository repository,
            PaymentFiles paymentFiles,
            AuditTrail auditTrail,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.paymentFiles = paymentFiles;
        this.auditTrail = auditTrail;
        this.events = events;
    }

    @Override
    public Step step() {
        return Step.DELIVER;
    }

    /**
     * The SFTP I/O is bounded by the session factory's timeouts, not by the deadline.
     */
    @Override
    public Completion perform(UUID fileId, Instant deadline) {
        StoredStatusReport report = repository.findByFile(fileId);
        paymentFiles.writeStatusReport(fileId, report.content());
        return () -> {
            repository.markDelivered(fileId);
            paymentFiles.markReplied(fileId);
            var delivered = new StatusReportDelivered(fileId, report.messageId(), report.groupStatus());
            auditTrail.record(fileId, delivered);
            events.publishEvent(delivered);
            LOG.info("Delivered status report {} with group status {}", report.messageId(), report.groupStatus());
        };
    }
}
