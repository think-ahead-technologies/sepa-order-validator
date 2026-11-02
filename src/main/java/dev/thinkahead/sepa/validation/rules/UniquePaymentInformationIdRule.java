// ABOUTME: Rule: every PmtInfId must be unique within one payment file.
// ABOUTME: Stateful, so one instance per file; reports ISO 20022 reason code DU02 (DuplicatePaymentInformationID).
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.PaymentInformation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UniquePaymentInformationIdRule implements ValidationRule<PaymentInformation> {

    private final Set<String> seen = new HashSet<>();

    @Override
    public List<Violation> validate(PaymentInformation paymentInformation) {
        if (seen.add(paymentInformation.paymentInformationId())) {
            return List.of();
        }
        return List.of(new Violation(
                "DU02",
                paymentInformation.path() + "/PmtInfId",
                "Payment information id " + paymentInformation.paymentInformationId() + " occurs more than once"));
    }
}
