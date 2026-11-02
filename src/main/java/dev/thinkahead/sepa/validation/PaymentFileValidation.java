// ABOUTME: The VALIDATE processing step: validates a received file outside any transaction, then records the outcome.
// ABOUTME: Recording registers the MsgId (DU01) and schedules REPORT; a container's messages are received instead.
package dev.thinkahead.sepa.validation;

import dev.thinkahead.sepa.audit.AuditTrail;
import dev.thinkahead.sepa.intake.PaymentFiles;
import dev.thinkahead.sepa.intake.Submission;
import dev.thinkahead.sepa.processing.Completion;
import dev.thinkahead.sepa.processing.ProcessingStep;
import dev.thinkahead.sepa.processing.Step;
import dev.thinkahead.sepa.processing.WorkQueue;
import dev.thinkahead.sepa.validation.container.ContainerReading;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class PaymentFileValidation implements ProcessingStep {

    static final String DUPLICATE_MESSAGE_ID = "DU01";

    private static final Logger LOG = LoggerFactory.getLogger(PaymentFileValidation.class);

    private final PaymentFiles paymentFiles;
    private final PaymentFileValidator validator;
    private final AcceptedMessages acceptedMessages;
    private final ValidationOutcomeRepository outcomes;
    private final WorkQueue workQueue;
    private final AuditTrail auditTrail;
    private final ApplicationEventPublisher events;

    PaymentFileValidation(
            PaymentFiles paymentFiles,
            PaymentFileValidator validator,
            AcceptedMessages acceptedMessages,
            ValidationOutcomeRepository outcomes,
            WorkQueue workQueue,
            AuditTrail auditTrail,
            ApplicationEventPublisher events) {
        this.paymentFiles = paymentFiles;
        this.validator = validator;
        this.acceptedMessages = acceptedMessages;
        this.outcomes = outcomes;
        this.workQueue = workQueue;
        this.auditTrail = auditTrail;
        this.events = events;
    }

    @Override
    public Step step() {
        return Step.VALIDATE;
    }

    @Override
    public Completion perform(UUID fileId, Instant deadline) {
        if (!paymentFiles.isAwaitingValidation(fileId)) {
            return () -> {};
        }
        Optional<byte[]> content = paymentFiles.content(fileId);
        if (content.isEmpty()) {
            return () -> record(fileId, ValidationResult.tooLarge());
        }
        Optional<ContainerReading> container = readContainerOrRejectTechnically(fileId, content.get(), deadline);
        if (container.isPresent()) {
            return switch (container.get()) {
                case ContainerReading.Fault fault ->
                    () -> record(fileId, new ValidationResult(null, null, fault.violations()));
                case ContainerReading.Split split -> () -> recordSplit(fileId, split.messages());
            };
        }
        ValidationResult result =
                validateOrRejectTechnically(fileId, content.get(), deadline, paymentFiles.submission(fileId));
        return () -> record(fileId, result);
    }

    /**
     * The customer always gets a reply: a file whose validation never completed is rejected technically.
     */
    @Override
    public Optional<Completion> whenAttemptsExhausted(UUID fileId) {
        return Optional.of(() -> record(fileId, ValidationResult.technicalError(fileId)));
    }

    /**
     * Validation is a pure function of the file content, so an unexpected exception would recur on every retry.
     * The customer gets a technical rejection instead of no reply, and operations get an ERROR to investigate.
     */
    private ValidationResult validateOrRejectTechnically(
            UUID fileId, byte[] content, Instant deadline, Submission submission) {
        try {
            return validator.validate(content, deadline, submission);
        } catch (RuntimeException e) {
            LOG.error("Validation of payment file {} failed unexpectedly; rejecting it technically", fileId, e);
            return ValidationResult.technicalError(fileId);
        }
    }

    private Optional<ContainerReading> readContainerOrRejectTechnically(UUID fileId, byte[] content, Instant deadline) {
        try {
            return validator.readContainer(content, deadline);
        } catch (RuntimeException e) {
            LOG.error("Reading payment file {} as container failed unexpectedly; rejecting it technically", fileId, e);
            return Optional.of(new ContainerReading.Fault(
                    ValidationResult.technicalError(fileId).violations()));
        }
    }

    /**
     * Receives each message of the container as a payment file of its own, scheduled for validation in this
     * transaction; the container itself ends as split and gets no status report (spec container-split).
     */
    private void recordSplit(UUID fileId, List<byte[]> messages) {
        if (!paymentFiles.isAwaitingValidation(fileId)) {
            return;
        }
        for (int index = 1; index <= messages.size(); index++) {
            paymentFiles.receiveFromContainer(fileId, index, messages.get(index - 1));
        }
        if (!paymentFiles.recordSplit(fileId)) {
            throw new IllegalStateException("Validation outcome of payment file " + fileId + " changed concurrently");
        }
        var split = new PaymentFileSplit(fileId, messages.size());
        auditTrail.record(fileId, split);
        events.publishEvent(split);
        LOG.info("Split container into {} messages", messages.size());
    }

    private void record(UUID fileId, ValidationResult result) {
        if (!paymentFiles.isAwaitingValidation(fileId)) {
            return;
        }
        ValidationResult outcome = result;
        if (outcome.accepted()
                && !acceptedMessages.register(paymentFiles.submitter(fileId), outcome.originalMessageId(), fileId)) {
            outcome = outcome.withViolation(new Violation(
                    DUPLICATE_MESSAGE_ID,
                    "GrpHdr/MsgId",
                    "Message id " + outcome.originalMessageId() + " was already submitted and accepted"));
        }
        if (!paymentFiles.recordValidationOutcome(fileId, outcome.accepted())) {
            throw new IllegalStateException("Validation outcome of payment file " + fileId + " changed concurrently");
        }
        var validated = new PaymentFileValidated(
                fileId,
                outcome.originalMessageId(),
                outcome.originalMessageNameId(),
                outcome.accepted(),
                outcome.violations());
        outcomes.insert(validated);
        workQueue.enqueue(fileId, Step.REPORT);
        auditTrail.record(fileId, validated);
        events.publishEvent(validated);
        logOutcome(outcome);
    }

    /**
     * Logs reason codes only: violation messages can quote values from the file.
     */
    private static void logOutcome(ValidationResult result) {
        if (result.accepted()) {
            LOG.info("Validated payment file: accepted");
        } else {
            LOG.info(
                    "Validated payment file: rejected with reason codes {}",
                    result.violations().stream()
                            .map(Violation::reasonCode)
                            .distinct()
                            .toList());
        }
    }
}
