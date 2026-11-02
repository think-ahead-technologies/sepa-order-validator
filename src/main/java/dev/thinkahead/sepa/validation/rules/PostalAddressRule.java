// ABOUTME: Rule: debtor and creditor addresses are structured or hybrid (TwnNm and Ctry), with at most two AdrLine.
// ABOUTME: Applies at any execution date, as the DK subset GBIC_5 requires; reports RR02 (debtor) or RR03 (creditor).
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.Party;
import dev.thinkahead.sepa.validation.pain001.PostalAddress;
import java.util.ArrayList;
import java.util.List;

public class PostalAddressRule implements ValidationRule<PostalAddress> {

    // EPC C2PSP IG 2025 1.7, 2.23 and 2.118; DK TVS GBIC_5 makes TwnNm and Ctry mandatory in these addresses.
    private static final int MAXIMUM_ADDRESS_LINES = 2;

    @Override
    public List<Violation> validate(PostalAddress address) {
        String reasonCode = address.party() == Party.DEBTOR ? "RR02" : "RR03";
        var violations = new ArrayList<Violation>();
        if (address.addressLineCount() > MAXIMUM_ADDRESS_LINES) {
            violations.add(new Violation(
                    reasonCode,
                    address.path() + "/AdrLine",
                    "At most " + MAXIMUM_ADDRESS_LINES + " address lines are allowed, found "
                            + address.addressLineCount()));
        }
        if (!(address.townName() && address.country())) {
            violations.add(new Violation(reasonCode, address.path(), "Address must contain TwnNm and Ctry"));
        }
        return List.copyOf(violations);
    }
}
