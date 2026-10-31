// ABOUTME: Rule: debtor and creditor addresses are structured or hybrid (TwnNm and Ctry), with at most two AdrLine.
// ABOUTME: Unstructured addresses stay allowed for execution before 22 November 2026; reports RR02 or RR03.
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.Party;
import dev.thinkahead.sepa.validation.pain001.PostalAddress;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PostalAddressRule implements ValidationRule<PostalAddress> {

    // EPC C2PSP IG 2025 1.7, 2.23 and 2.118: from this date only structured and hybrid addresses are allowed.
    static final LocalDate STRUCTURED_ADDRESS_REQUIRED_FROM = LocalDate.of(2026, 11, 22);

    private static final int MAXIMUM_ADDRESS_LINES = 2;

    @Override
    public List<Violation> validate(PostalAddress address) {
        String reasonCode = address.party() == Party.DEBTOR ? "RR02" : "RR03";
        var violations = new ArrayList<Violation>();
        if (address.addressLineCount() > MAXIMUM_ADDRESS_LINES) {
            violations.add(new Violation(reasonCode, address.path() + "/AdrLine", "At most "
                    + MAXIMUM_ADDRESS_LINES + " address lines are allowed, found " + address.addressLineCount()));
        }
        boolean structuredOrHybrid = address.townName() && address.country();
        boolean unstructured = address.addressLineCount() > 0 && !address.otherElements();
        if (!structuredOrHybrid && structuredAddressRequired(address.requestedExecutionDate())) {
            violations.add(new Violation(reasonCode, address.path(), "Address must contain TwnNm and Ctry"
                    + " for execution on or after " + STRUCTURED_ADDRESS_REQUIRED_FROM));
        } else if (!structuredOrHybrid && !unstructured) {
            violations.add(new Violation(reasonCode, address.path(),
                    "Address must contain TwnNm and Ctry, or consist only of AdrLine and Ctry"));
        }
        return List.copyOf(violations);
    }

    /**
     * The execution date is mandatory in a schema-valid file; without one, the stricter rule applies.
     */
    private static boolean structuredAddressRequired(LocalDate requestedExecutionDate) {
        return requestedExecutionDate == null || !requestedExecutionDate.isBefore(STRUCTURED_ADDRESS_REQUIRED_FROM);
    }
}
