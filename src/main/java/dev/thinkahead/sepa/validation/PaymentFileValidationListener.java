// ABOUTME: Validates each received payment file asynchronously and records the outcome with intake.
// ABOUTME: Publishes PaymentFileValidated in the same transaction as the status update.
package dev.thinkahead.sepa.validation;

import dev.thinkahead.sepa.intake.PaymentFileReceived;
import dev.thinkahead.sepa.intake.PaymentFiles;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class PaymentFileValidationListener {

    private final PaymentFiles paymentFiles;
    private final PaymentFileValidator validator;
    private final ApplicationEventPublisher events;

    PaymentFileValidationListener(PaymentFiles paymentFiles, PaymentFileValidator validator,
            ApplicationEventPublisher events) {
        this.paymentFiles = paymentFiles;
        this.validator = validator;
        this.events = events;
    }

    @ApplicationModuleListener
    void on(PaymentFileReceived received) {
        ValidationResult result = validator.validate(paymentFiles.content(received.fileId()));
        paymentFiles.recordValidationOutcome(received.fileId(), result.accepted());
        events.publishEvent(new PaymentFileValidated(received.fileId(), result.originalMessageId(),
                result.originalMessageNameId(), result.accepted(), result.violations()));
    }
}
