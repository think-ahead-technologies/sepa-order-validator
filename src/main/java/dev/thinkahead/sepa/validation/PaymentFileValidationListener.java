// ABOUTME: Validates each received payment file asynchronously and records the outcome with intake.
// ABOUTME: Acceptance, MsgId registration (duplicate check) and event publication share one transaction.
package dev.thinkahead.sepa.validation;

import dev.thinkahead.sepa.intake.PaymentFileReceived;
import dev.thinkahead.sepa.intake.PaymentFiles;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class PaymentFileValidationListener {

    static final String DUPLICATE_MESSAGE_ID = "DU01";

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
        ValidationResult result = validator.validate(paymentFiles.content(received.fileId()));
        if (result.accepted()
                && !acceptedMessages.register(received.submitter(), result.originalMessageId(), received.fileId())) {
            result = result.withViolation(new Violation(DUPLICATE_MESSAGE_ID, "GrpHdr/MsgId",
                    "Message id " + result.originalMessageId() + " was already submitted and accepted"));
        }
        paymentFiles.recordValidationOutcome(received.fileId(), result.accepted());
        events.publishEvent(new PaymentFileValidated(received.fileId(), result.originalMessageId(),
                result.originalMessageNameId(), result.accepted(), result.violations()));
    }
}
