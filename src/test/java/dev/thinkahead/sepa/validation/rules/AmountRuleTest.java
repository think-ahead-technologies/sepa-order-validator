// ABOUTME: Unit tests for the SEPA amount rule: 0.01 to 999,999,999.99 with at most two decimal places.
// ABOUTME: Checks the reason codes AM01, AM02 and AM12 and the reported location of the instructed amount.
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import dev.thinkahead.sepa.validation.pain001.RemittanceInformation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AmountRuleTest {

    private final AmountRule rule = new AmountRule();

    private static CreditTransferTransaction transaction(String amount) {
        return new CreditTransferTransaction("PmtInf[1]/CdtTrfTxInf[3]", "E2E-003",
                amount == null ? null : new BigDecimal(amount), "EUR", "NL91ABNA0417164300", null,
                RemittanceInformation.NONE, "Lieferant AG");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1", "50.5", "100.00", "999999999.99", "1.500"})
    void acceptsAmountsWithinTheSepaRange(String amount) {
        assertThat(rule.validate(transaction(amount))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00"})
    void reportsAm01ForAZeroAmount(String amount) {
        assertThat(rule.validate(transaction(amount)))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("AM01");
                    assertThat(v.path()).isEqualTo("PmtInf[1]/CdtTrfTxInf[3]/Amt/InstdAmt");
                });
    }

    @Test
    void reportsAm02AboveTheSepaMaximum() {
        assertThat(rule.validate(transaction("1000000000.00")))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("AM02");
                    assertThat(v.path()).isEqualTo("PmtInf[1]/CdtTrfTxInf[3]/Amt/InstdAmt");
                    assertThat(v.message()).contains("999999999.99");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.001", "0.001", "12.34567"})
    void reportsAm12ForMoreThanTwoDecimalPlaces(String amount) {
        assertThat(rule.validate(transaction(amount)))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("AM12");
                    assertThat(v.path()).isEqualTo("PmtInf[1]/CdtTrfTxInf[3]/Amt/InstdAmt");
                });
    }

    @Test
    void reportsEveryAmountViolation() {
        assertThat(rule.validate(transaction("1000000000.001")))
                .extracting(Violation::reasonCode)
                .containsExactly("AM02", "AM12");
    }

    @Test
    void leavesAMissingInstructedAmountToTheCurrencyRule() {
        assertThat(rule.validate(transaction(null))).isEmpty();
    }
}
