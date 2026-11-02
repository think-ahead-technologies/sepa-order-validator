// ABOUTME: Rule: SEPA limits the names of initiating party, debtor, creditor and ultimate parties to 70 characters.
// ABOUTME: The ISO schema allows 140; longer names are reported with FF01 (InvalidFileFormat).
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.PartyName;
import java.util.List;

public class PartyNameLengthRule implements ValidationRule<PartyName> {

    private static final int MAXIMUM_LENGTH = 70;

    @Override
    public List<Violation> validate(PartyName partyName) {
        int length = partyName.name().codePointCount(0, partyName.name().length());
        if (length <= MAXIMUM_LENGTH) {
            return List.of();
        }
        return List.of(new Violation(
                "FF01", partyName.path(), "Name has " + length + " characters, SEPA allows at most " + MAXIMUM_LENGTH));
    }
}
