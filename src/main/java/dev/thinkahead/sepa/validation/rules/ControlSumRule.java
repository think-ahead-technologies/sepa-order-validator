// ABOUTME: Rule: a declared CtrlSum must equal the sum of the contained transaction amounts.
// ABOUTME: Reports ISO 20022 reason code AM10 (InvalidControlSum); CtrlSum is optional and checked only when present.
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.Totals;
import java.util.List;

public class ControlSumRule implements ValidationRule<Totals> {

    @Override
    public List<Violation> validate(Totals totals) {
        if (totals.declaredControlSum() == null
                || totals.declaredControlSum().compareTo(totals.actualControlSum()) == 0) {
            return List.of();
        }
        return List.of(new Violation(
                "AM10",
                totals.path() + "/CtrlSum",
                "Declared control sum " + totals.declaredControlSum().toPlainString() + ", actual sum "
                        + totals.actualControlSum().toPlainString()));
    }
}
