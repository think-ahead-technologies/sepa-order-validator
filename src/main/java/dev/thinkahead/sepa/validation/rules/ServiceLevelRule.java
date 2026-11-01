// ABOUTME: Service level of SEPA credit transfers and SEPA Instant: only the code SEPA, if given (GBIC_5 TVS, AT-T001).
// ABOUTME: The DK schema limits the code only in length, so other codes such as URGP are rejected here with AG02.
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.PaymentTypeInformation;
import java.util.List;

public class ServiceLevelRule implements ValidationRule<PaymentTypeInformation> {

    private static final String SEPA = "SEPA";

    @Override
    public List<Violation> validate(PaymentTypeInformation paymentType) {
        String code = paymentType.serviceLevelCode();
        if (code == null || code.equals(SEPA)) {
            return List.of();
        }
        return List.of(new Violation("AG02", paymentType.path() + "/SvcLvl/Cd",
                "Service level " + code + " is not allowed, SEPA credit transfers use SEPA"));
    }
}
