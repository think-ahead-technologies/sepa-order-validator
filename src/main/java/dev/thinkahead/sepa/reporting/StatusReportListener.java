// ABOUTME: Turns each validation outcome into a pain.002 report, stores it and delivers it to the customer.
// ABOUTME: Report message ids are "STS" plus a UUID without dashes, exactly fitting ISO Max35Text.
package dev.thinkahead.sepa.reporting;

import dev.thinkahead.sepa.intake.PaymentFiles;
import dev.thinkahead.sepa.validation.PaymentFileValidated;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class StatusReportListener {

    private final Pain002Writer writer;
    private final StatusReportRepository repository;
    private final PaymentFiles paymentFiles;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    StatusReportListener(Pain002Writer writer, StatusReportRepository repository, PaymentFiles paymentFiles,
            ApplicationEventPublisher events, Clock clock) {
        this.writer = writer;
        this.repository = repository;
        this.paymentFiles = paymentFiles;
        this.events = events;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(PaymentFileValidated validated) {
        var report = new StatusReport(
                "STS" + UUID.randomUUID().toString().replace("-", ""),
                OffsetDateTime.now(clock),
                validated.originalMessageId(),
                validated.originalMessageNameId(),
                validated.accepted() ? GroupStatus.ACCP : GroupStatus.RJCT,
                validated.violations());
        byte[] content = writer.write(report);

        repository.insert(validated.fileId(), report, content);
        paymentFiles.deliverStatusReport(validated.fileId(), content);
        events.publishEvent(new StatusReportDelivered(validated.fileId(), report.messageId(), report.groupStatus()));
    }
}
