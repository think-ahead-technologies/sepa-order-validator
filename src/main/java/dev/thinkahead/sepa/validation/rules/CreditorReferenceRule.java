// ABOUTME: Rule: a creditor reference needs type and reference, and a coded type must be SCOR (EPC IG 2.169-2.175).
// ABOUTME: Check digits are not validated, as EPC does not require it of the receiving PSP; reports FF01.
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import dev.thinkahead.sepa.validation.pain001.CreditorReference;
import java.util.ArrayList;
import java.util.List;

public class CreditorReferenceRule implements ValidationRule<CreditTransferTransaction> {

    @Override
    public List<Violation> validate(CreditTransferTransaction transaction) {
        CreditorReference reference = transaction.remittanceInformation().creditorReference();
        if (reference == null) {
            return List.of();
        }
        String path = transaction.path() + "/RmtInf/Strd/CdtrRefInf";
        var violations = new ArrayList<Violation>();
        if (!reference.typePresent()) {
            violations.add(new Violation("FF01", path + "/Tp", "Creditor reference type is missing"));
        } else if (reference.typeCode() != null && !reference.typeCode().equals("SCOR")) {
            violations.add(new Violation(
                    "FF01",
                    path + "/Tp/CdOrPrtry/Cd",
                    "Creditor reference type " + reference.typeCode() + " is not allowed, SEPA allows only SCOR"));
        }
        if (reference.reference() == null) {
            violations.add(new Violation("FF01", path + "/Ref", "Creditor reference is missing"));
        }
        return List.copyOf(violations);
    }
}
