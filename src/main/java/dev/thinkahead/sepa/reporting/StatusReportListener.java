// ABOUTME: Turns each validation outcome into exactly one stored pain.002 report and schedules its delivery.
// ABOUTME: Report message ids are "STS" plus a UUID without dashes, exactly fitting ISO Max35Text.
package dev.thinkahead.sepa.reporting;

import dev.thinkahead.sepa.LogContext;
import dev.thinkahead.sepa.processing.Step;
import dev.thinkahead.sepa.processing.WorkQueue;
import dev.thinkahead.sepa.validation.PaymentFileValidated;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class StatusReportListener {

    private final Pain002Writer writer;
    private final StatusReportRepository repository;
    private final WorkQueue workQueue;
    private final Clock clock;

    StatusReportListener(Pain002Writer writer, StatusReportRepository repository, WorkQueue workQueue, Clock clock) {
        this.writer = writer;
        this.repository = repository;
        this.workQueue = workQueue;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(PaymentFileValidated validated) {
        LogContext.setFile(validated.fileId());
        if (repository.existsForFile(validated.fileId())) {
            return;
        }
        var report = new StatusReport(
                "STS" + UUID.randomUUID().toString().replace("-", ""),
                OffsetDateTime.now(clock),
                validated.originalMessageId(),
                validated.originalMessageNameId(),
                validated.accepted() ? GroupStatus.ACCP : GroupStatus.RJCT,
                validated.violations());
        if (repository.insertIfAbsent(validated.fileId(), report, writer.write(report))) {
            workQueue.enqueue(validated.fileId(), Step.DELIVER);
        }
    }
}
