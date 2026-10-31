// ABOUTME: Rule: the content matches how the file was submitted, as DK order types CCT and CIP and containers require.
// ABOUTME: No SEPA Instant in containers (Anlage 3 V3.9, 2.4); only SEPA Instant in the SEPA Instant inbox. AG02.
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.intake.Submission;
import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferScheme;
import dev.thinkahead.sepa.validation.pain001.PaymentInformation;
import java.util.List;

public class SubmissionRule implements ValidationRule<PaymentInformation> {

    private final Submission submission;

    public SubmissionRule(Submission submission) {
        this.submission = submission;
    }

    @Override
    public List<Violation> validate(PaymentInformation paymentInformation) {
        boolean instant = paymentInformation.scheme() == CreditTransferScheme.SCT_INST;
        if (instant && submission.fromContainer()) {
            return List.of(new Violation("AG02", paymentInformation.path(),
                    "SEPA Instant credit transfers must not be submitted in a container, only as a single message"));
        }
        if (!instant && submission.instantOrder()) {
            return List.of(new Violation("AG02", paymentInformation.path(),
                    "The SEPA Instant inbox accepts only SEPA Instant credit transfers"));
        }
        return List.of();
    }
}
