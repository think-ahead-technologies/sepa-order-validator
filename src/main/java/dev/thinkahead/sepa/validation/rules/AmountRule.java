// ABOUTME: Rule: a SEPA credit transfer amount is 0.01 to 999,999,999.99 euro with at most two decimal places.
// ABOUTME: Reports AM01 (ZeroAmount), AM02 (NotAllowedAmount) and AM12 (InvalidAmount) per EPC C2PSP IG 2.95.
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class AmountRule implements ValidationRule<CreditTransferTransaction> {

    private static final BigDecimal MAXIMUM = new BigDecimal("999999999.99");

    @Override
    public List<Violation> validate(CreditTransferTransaction transaction) {
        BigDecimal amount = transaction.amount();
        if (amount == null) {
            return List.of();
        }
        String path = transaction.path() + "/Amt/InstdAmt";
        if (amount.signum() == 0) {
            return List.of(new Violation("AM01", path, "Amount must be at least 0.01"));
        }
        var violations = new ArrayList<Violation>();
        if (amount.compareTo(MAXIMUM) > 0) {
            violations.add(new Violation(
                    "AM02",
                    path,
                    "Amount " + amount.toPlainString() + " exceeds the SEPA maximum of " + MAXIMUM.toPlainString()));
        }
        // Trailing zeros are insignificant, as for the XSD fractionDigits facet: 1.500 has one decimal place.
        if (amount.stripTrailingZeros().scale() > 2) {
            violations.add(new Violation(
                    "AM12", path, "Amount " + amount.toPlainString() + " has more than two decimal places"));
        }
        return List.copyOf(violations);
    }
}
