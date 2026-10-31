// ABOUTME: Delivers a stored pain.002 report to the customer through the channel the payment file arrived on.
// ABOUTME: Always sends the stored bytes, so a repeated delivery after a failed commit re-sends the identical report.
package dev.thinkahead.sepa.reporting;

import dev.thinkahead.sepa.intake.PaymentFiles;
import dev.thinkahead.sepa.reporting.StatusReportRepository.StoredStatusReport;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class StatusReportDeliveryListener {

    private final StatusReportRepository repository;
    private final PaymentFiles paymentFiles;
    private final ApplicationEventPublisher events;

    StatusReportDeliveryListener(StatusReportRepository repository, PaymentFiles paymentFiles,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.paymentFiles = paymentFiles;
        this.events = events;
    }

    @ApplicationModuleListener
    void on(StatusReportCreated created) {
        StoredStatusReport report = repository.findByFile(created.fileId());
        paymentFiles.deliverStatusReport(created.fileId(), report.content());
        repository.markDelivered(created.fileId());
        events.publishEvent(new StatusReportDelivered(created.fileId(), report.messageId(), report.groupStatus()));
    }
}
