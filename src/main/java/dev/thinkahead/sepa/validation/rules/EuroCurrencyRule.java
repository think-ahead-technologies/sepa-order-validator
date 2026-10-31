// ABOUTME: Rule: SEPA credit transfers must be denominated in euro.
// ABOUTME: Reports ISO 20022 reason code AM03 (NotAllowedCurrency).
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import java.util.List;

public class EuroCurrencyRule implements ValidationRule<CreditTransferTransaction> {

    @Override
    public List<Violation> validate(CreditTransferTransaction transaction) {
        if ("EUR".equals(transaction.currency())) {
            return List.of();
        }
        return List.of(new Violation("AM03", transaction.path() + "/Amt/InstdAmt/@Ccy",
                "Currency " + transaction.currency() + " is not allowed, SEPA requires EUR"));
    }
}
