// ABOUTME: Rule: every SEPA credit transfer names its creditor, the beneficiary (EPC C2PSP IG 2.117, AT-E001).
// ABOUTME: Reports ISO 20022 reason code RR03 (MissingCreditorNameOrAddress).
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import java.util.List;

public class CreditorNameRule implements ValidationRule<CreditTransferTransaction> {

    @Override
    public List<Violation> validate(CreditTransferTransaction transaction) {
        if (transaction.creditorName() != null) {
            return List.of();
        }
        return List.of(new Violation("RR03", transaction.path() + "/Cdtr/Nm", "Creditor name is missing"));
    }
}
