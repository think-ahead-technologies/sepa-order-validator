// ABOUTME: The REPORT processing step: builds the pain.002 for a validated file, then stores it and schedules DELIVER.
// ABOUTME: Report message ids are "STS" plus a UUID without dashes, exactly fitting ISO Max35Text.
package dev.thinkahead.sepa.reporting;

import dev.thinkahead.sepa.processing.Completion;
import dev.thinkahead.sepa.processing.ProcessingStep;
import dev.thinkahead.sepa.processing.Step;
import dev.thinkahead.sepa.processing.WorkQueue;
import dev.thinkahead.sepa.validation.PaymentFileValidated;
import dev.thinkahead.sepa.validation.ValidationOutcomes;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class StatusReportCreation implements ProcessingStep {

    private final ValidationOutcomes validationOutcomes;
    private final Pain002Writer writer;
    private final StatusReportRepository repository;
    private final WorkQueue workQueue;
    private final Clock clock;

    StatusReportCreation(
            ValidationOutcomes validationOutcomes,
            Pain002Writer writer,
            StatusReportRepository repository,
            WorkQueue workQueue,
            Clock clock) {
        this.validationOutcomes = validationOutcomes;
        this.writer = writer;
        this.repository = repository;
        this.workQueue = workQueue;
        this.clock = clock;
    }

    @Override
    public Step step() {
        return Step.REPORT;
    }

    /**
     * A file keeps the report it already has, so a repeated REPORT never creates a second report or message id.
     */
    @Override
    public Completion perform(UUID fileId, Instant deadline) {
        if (repository.existsForFile(fileId)) {
            return () -> workQueue.enqueue(fileId, Step.DELIVER);
        }
        PaymentFileValidated validated = validationOutcomes.find(fileId);
        var report = new StatusReport(
                "STS" + UUID.randomUUID().toString().replace("-", ""),
                OffsetDateTime.now(clock),
                validated.originalMessageId(),
                validated.originalMessageNameId(),
                validated.accepted() ? GroupStatus.ACCP : GroupStatus.RJCT,
                validated.violations());
        byte[] content = writer.write(report);
        return () -> {
            repository.insertIfAbsent(fileId, report, content);
            workQueue.enqueue(fileId, Step.DELIVER);
        };
    }
}
