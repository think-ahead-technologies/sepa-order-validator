// ABOUTME: Rule: the only local instrument is the code INST for SEPA Instant; SEPA credit transfers carry none.
// ABOUTME: DK Anlage 3 v3.9, 2.2.4.1 (Prtry is not used); reports AG02 (InvalidBankOperationCode).
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.PaymentTypeInformation;
import java.util.List;

public class LocalInstrumentRule implements ValidationRule<PaymentTypeInformation> {

    @Override
    public List<Violation> validate(PaymentTypeInformation paymentType) {
        String path = paymentType.path() + "/LclInstrm";
        if (paymentType.localInstrumentProprietary() != null) {
            return List.of(new Violation("AG02", path,
                    "Proprietary local instrument is not allowed, use the code INST for SEPA Instant"));
        }
        String code = paymentType.localInstrumentCode();
        if (code == null || code.equals("INST")) {
            return List.of();
        }
        return List.of(new Violation("AG02", path,
                "Local instrument " + code + " is not allowed, only INST for SEPA Instant"));
    }
}
