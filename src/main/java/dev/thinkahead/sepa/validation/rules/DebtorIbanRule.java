// ABOUTME: Rule: the debtor account of every payment information block must be a valid SEPA IBAN.
// ABOUTME: Reports ISO 20022 reason code AC02 (InvalidDebtorAccountNumber).
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.PaymentInformation;
import java.util.List;

public class DebtorIbanRule implements ValidationRule<PaymentInformation> {

    @Override
    public List<Violation> validate(PaymentInformation paymentInformation) {
        String iban = paymentInformation.debtorIban();
        if (IbanCheck.isValid(iban)) {
            return List.of();
        }
        String message = iban == null
                ? "Debtor account is not identified by an IBAN"
                : "Debtor account is not a valid IBAN: " + iban;
        return List.of(new Violation("AC02", paymentInformation.path() + "/DbtrAcct/Id/IBAN", message));
    }
}
