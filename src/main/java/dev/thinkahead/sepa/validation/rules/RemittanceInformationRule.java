// ABOUTME: Rule: a SEPA credit transfer carries either one unstructured or one structured remittance information.
// ABOUTME: Structured content including tags must not exceed 140 characters; violations are reported with FF01.
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import dev.thinkahead.sepa.validation.pain001.RemittanceInformation;
import java.util.ArrayList;
import java.util.List;

public class RemittanceInformationRule implements ValidationRule<CreditTransferTransaction> {

    private static final int MAXIMUM_STRUCTURED_LENGTH = 140;

    @Override
    public List<Violation> validate(CreditTransferTransaction transaction) {
        RemittanceInformation remittance = transaction.remittanceInformation();
        String path = transaction.path() + "/RmtInf";
        var violations = new ArrayList<Violation>();
        if (remittance.unstructuredCount() > 0 && remittance.structuredCount() > 0) {
            violations.add(new Violation("FF01", path,
                    "Remittance information must be either structured or unstructured, not both"));
        }
        if (remittance.unstructuredCount() > 1) {
            violations.add(new Violation("FF01", path + "/Ustrd", "Only one unstructured remittance information is"
                    + " allowed, found " + remittance.unstructuredCount()));
        }
        if (remittance.structuredCount() > 1) {
            violations.add(new Violation("FF01", path + "/Strd", "Only one structured remittance information is"
                    + " allowed, found " + remittance.structuredCount()));
        }
        if (remittance.longestStructuredLength() > MAXIMUM_STRUCTURED_LENGTH) {
            violations.add(new Violation("FF01", path + "/Strd", "Structured remittance information has "
                    + remittance.longestStructuredLength() + " characters including tags, SEPA allows at most "
                    + MAXIMUM_STRUCTURED_LENGTH));
        }
        return List.copyOf(violations);
    }
}
