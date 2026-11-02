// ABOUTME: Unit tests for the declared-versus-actual checks of NbOfTxs (AM18) and CtrlSum (AM10).
// ABOUTME: Covers group header and payment information level totals, including the optional CtrlSum.
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import dev.thinkahead.sepa.validation.pain001.Totals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TotalsRulesTest {

    @Test
    void numberOfTransactionsRuleAcceptsMatchingCount() {
        var totals = new Totals("GrpHdr", "2", null, 2, new BigDecimal("150.50"));

        assertThat(new NumberOfTransactionsRule().validate(totals)).isEmpty();
    }

    @Test
    void numberOfTransactionsRuleReportsAm18OnMismatch() {
        var totals = new Totals("GrpHdr", "3", null, 2, new BigDecimal("150.50"));

        assertThat(new NumberOfTransactionsRule().validate(totals))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("AM18");
                    assertThat(v.path()).isEqualTo("GrpHdr/NbOfTxs");
                    assertThat(v.message()).contains("3").contains("2");
                });
    }

    @Test
    void numberOfTransactionsRuleIgnoresAbsentDeclaration() {
        var totals = new Totals("PmtInf[1]", null, null, 2, new BigDecimal("150.50"));

        assertThat(new NumberOfTransactionsRule().validate(totals)).isEmpty();
    }

    @Test
    void controlSumRuleAcceptsNumericallyEqualSums() {
        var totals = new Totals("PmtInf[1]", "2", new BigDecimal("150.5"), 2, new BigDecimal("150.50"));

        assertThat(new ControlSumRule().validate(totals)).isEmpty();
    }

    @Test
    void controlSumRuleReportsAm10OnMismatch() {
        var totals = new Totals("PmtInf[1]", "2", new BigDecimal("150.51"), 2, new BigDecimal("150.50"));

        assertThat(new ControlSumRule().validate(totals)).singleElement().satisfies(v -> {
            assertThat(v.reasonCode()).isEqualTo("AM10");
            assertThat(v.path()).isEqualTo("PmtInf[1]/CtrlSum");
        });
    }

    @Test
    void controlSumRuleIgnoresAbsentControlSum() {
        var totals = new Totals("GrpHdr", "2", null, 2, new BigDecimal("150.50"));

        assertThat(new ControlSumRule().validate(totals)).isEmpty();
    }
}
