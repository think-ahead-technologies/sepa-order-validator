// ABOUTME: The VALIDATE processing step: validates a received file outside any transaction, then records the outcome.
// ABOUTME: Recording registers the MsgId (DU01), moves the file status and publishes PaymentFileValidated at once.
package dev.thinkahead.sepa.validation;

import dev.thinkahead.sepa.intake.PaymentFiles;
import dev.thinkahead.sepa.processing.Completion;
import dev.thinkahead.sepa.processing.ProcessingStep;
import dev.thinkahead.sepa.processing.Step;
import java.time.Instant;
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
    private final ApplicationEventPublisher events;

    PaymentFileValidation(PaymentFiles paymentFiles, PaymentFileValidator validator,
            AcceptedMessages acceptedMessages, ApplicationEventPublisher events) {
        this.paymentFiles = paymentFiles;
        this.validator = validator;
        this.acceptedMessages = acceptedMessages;
        this.events = events;
    }

    @Override
    public Step step() {
        return Step.VALIDATE;
    }

    @Override
    public Completion perform(UUID fileId, Instant deadline) {
        if (!paymentFiles.isAwaitingValidation(fileId)) {
            return () -> {
            };
        }
        ValidationResult result = paymentFiles.content(fileId)
                .map(content -> validateOrRejectTechnically(fileId, content, deadline))
                .orElseGet(ValidationResult::tooLarge);
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
    private ValidationResult validateOrRejectTechnically(UUID fileId, byte[] content, Instant deadline) {
        try {
            return validator.validate(content, deadline);
        } catch (RuntimeException e) {
            LOG.error("Validation of payment file {} failed unexpectedly; rejecting it technically", fileId, e);
            return ValidationResult.technicalError(fileId);
        }
    }

    private void record(UUID fileId, ValidationResult result) {
        if (!paymentFiles.isAwaitingValidation(fileId)) {
            return;
        }
        ValidationResult outcome = result;
        if (outcome.accepted() && !acceptedMessages.register(paymentFiles.submitter(fileId),
                outcome.originalMessageId(), fileId)) {
            outcome = outcome.withViolation(new Violation(DUPLICATE_MESSAGE_ID, "GrpHdr/MsgId",
                    "Message id " + outcome.originalMessageId() + " was already submitted and accepted"));
        }
        if (!paymentFiles.recordValidationOutcome(fileId, outcome.accepted())) {
            throw new IllegalStateException("Validation outcome of payment file " + fileId + " changed concurrently");
        }
        events.publishEvent(new PaymentFileValidated(fileId, outcome.originalMessageId(),
                outcome.originalMessageNameId(), outcome.accepted(), outcome.violations()));
        logOutcome(outcome);
    }

    /**
     * Logs reason codes only: violation messages can quote values from the file.
     */
    private static void logOutcome(ValidationResult result) {
        if (result.accepted()) {
            LOG.info("Validated payment file: accepted");
        } else {
            LOG.info("Validated payment file: rejected with reason codes {}",
                    result.violations().stream().map(Violation::reasonCode).distinct().toList());
        }
    }
}
