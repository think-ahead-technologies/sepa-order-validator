// ABOUTME: Rule: a declared NbOfTxs must equal the number of transactions actually contained.
// ABOUTME: Reports ISO 20022 reason code AM18 (InvalidNumberOfTransactions); absent declarations are not checked.
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.Totals;
import java.util.List;

public class NumberOfTransactionsRule implements ValidationRule<Totals> {

    @Override
    public List<Violation> validate(Totals totals) {
        String declared = totals.declaredNumberOfTransactions();
        if (declared == null || Long.parseLong(declared) == totals.actualNumberOfTransactions()) {
            return List.of();
        }
        return List.of(new Violation(
                "AM18",
                totals.path() + "/NbOfTxs",
                "Declared " + declared + " transactions, found " + totals.actualNumberOfTransactions()));
    }
}
