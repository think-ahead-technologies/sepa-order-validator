// ABOUTME: Validates each received payment file asynchronously and records the outcome with intake.
// ABOUTME: Safe to run more than once per file: only the first delivery of the event records and publishes an outcome.
package dev.thinkahead.sepa.validation;

import dev.thinkahead.sepa.LogContext;
import dev.thinkahead.sepa.intake.PaymentFileReceived;
import dev.thinkahead.sepa.intake.PaymentFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Component
class PaymentFileValidationListener {

    static final String DUPLICATE_MESSAGE_ID = "DU01";

    private static final Logger LOG = LoggerFactory.getLogger(PaymentFileValidationListener.class);

    private final PaymentFiles paymentFiles;
    private final PaymentFileValidator validator;
    private final AcceptedMessages acceptedMessages;
    private final ApplicationEventPublisher events;

    PaymentFileValidationListener(PaymentFiles paymentFiles, PaymentFileValidator validator,
            AcceptedMessages acceptedMessages, ApplicationEventPublisher events) {
        this.paymentFiles = paymentFiles;
        this.validator = validator;
        this.acceptedMessages = acceptedMessages;
        this.events = events;
    }

    @ApplicationModuleListener
    void on(PaymentFileReceived received) {
        LogContext.setFile(received.fileId());
        if (!paymentFiles.isAwaitingValidation(received.fileId())) {
            return;
        }
        ValidationResult result = paymentFiles.content(received.fileId())
                .map(content -> validateOrRejectTechnically(received, content))
                .orElseGet(ValidationResult::tooLarge);
        if (result.accepted()
                && !acceptedMessages.register(received.submitter(), result.originalMessageId(), received.fileId())) {
            result = result.withViolation(new Violation(DUPLICATE_MESSAGE_ID, "GrpHdr/MsgId",
                    "Message id " + result.originalMessageId() + " was already submitted and accepted"));
        }
        if (!paymentFiles.recordValidationOutcome(received.fileId(), result.accepted())) {
            // A concurrent delivery of the same event recorded the outcome first; undo this attempt entirely.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return;
        }
        events.publishEvent(new PaymentFileValidated(received.fileId(), result.originalMessageId(),
                result.originalMessageNameId(), result.accepted(), result.violations()));
        logOutcome(result);
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

    /**
     * Validation is a pure function of the file content, so an unexpected exception would recur on every retry.
     * The customer gets a technical rejection instead of no reply, and operations get an ERROR to investigate.
     */
    private ValidationResult validateOrRejectTechnically(PaymentFileReceived received, byte[] content) {
        try {
            return validator.validate(content);
        } catch (RuntimeException e) {
            LOG.error("Validation of payment file {} failed unexpectedly; rejecting it technically", received.fileId(), e);
            return ValidationResult.technicalError(received.fileId());
        }
    }
}
