// ABOUTME: Rule: the creditor account of every credit transfer must be a valid SEPA IBAN.
// ABOUTME: Reports ISO 20022 reason code AC03 (InvalidCreditorAccountNumber).
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import java.util.List;

public class CreditorIbanRule implements ValidationRule<CreditTransferTransaction> {

    @Override
    public List<Violation> validate(CreditTransferTransaction transaction) {
        String iban = transaction.creditorIban();
        if (IbanCheck.isValid(iban)) {
            return List.of();
        }
        String message = iban == null
                ? "Creditor account is not identified by an IBAN"
                : "Creditor account is not a valid IBAN: " + iban;
        return List.of(new Violation("AC03", transaction.path() + "/CdtrAcct/Id/IBAN", message));
    }
}
