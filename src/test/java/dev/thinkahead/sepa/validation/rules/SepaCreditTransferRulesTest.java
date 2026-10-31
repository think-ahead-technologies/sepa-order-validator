// ABOUTME: Unit tests for the credit transfer rule set's cap on reported violations.
// ABOUTME: Beyond the cap, violations are only counted and summarised in one NARR entry.
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import dev.thinkahead.sepa.validation.pain001.RemittanceInformation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SepaCreditTransferRulesTest {

    @Test
    void reportsEveryViolationUpToTheCap() {
        var rules = new SepaCreditTransferRules(5);

        invalidTransactions(rules, 3);

        assertThat(rules.violations()).hasSize(3).extracting(Violation::reasonCode).containsOnly("AC03");
    }

    @Test
    void summarisesViolationsBeyondTheCapInOneNarrativeEntry() {
        var rules = new SepaCreditTransferRules(2);

        invalidTransactions(rules, 5);

        List<Violation> violations = rules.violations();
        assertThat(violations).hasSize(3);
        assertThat(violations.subList(0, 2)).extracting(Violation::reasonCode).containsOnly("AC03");
        assertThat(violations.get(2).reasonCode()).isEqualTo("NARR");
        assertThat(violations.get(2).message()).contains("3 more violations");
    }

    private static void invalidTransactions(SepaCreditTransferRules rules, int count) {
        for (int i = 1; i <= count; i++) {
            rules.transaction(new CreditTransferTransaction("PmtInf[1]/CdtTrfTxInf[" + i + "]", "E2E-" + i,
                    new BigDecimal("1.00"), "EUR", "NL91ABNA0417164301", null, RemittanceInformation.NONE));
        }
    }
}
