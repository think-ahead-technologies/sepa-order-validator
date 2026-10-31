// ABOUTME: Unit tests for the account and currency rules applied to payment information and transactions.
// ABOUTME: Checks the ISO 20022 reason codes and file locations reported for each violation.
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import dev.thinkahead.sepa.validation.pain001.PaymentInformation;
import dev.thinkahead.sepa.validation.pain001.RemittanceInformation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransactionRulesTest {

    private static CreditTransferTransaction transaction(String currency, String creditorIban) {
        return new CreditTransferTransaction("PmtInf[1]/CdtTrfTxInf[2]", "E2E-002",
                new BigDecimal("50.50"), currency, creditorIban, null, RemittanceInformation.NONE,
                "Lieferant AG");
    }

    @Test
    void creditorIbanRuleAcceptsValidIban() {
        assertThat(new CreditorIbanRule().validate(transaction("EUR", "NL91ABNA0417164300"))).isEmpty();
    }

    @Test
    void creditorIbanRuleReportsAc03WithLocation() {
        assertThat(new CreditorIbanRule().validate(transaction("EUR", "NL91ABNA0417164301")))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("AC03");
                    assertThat(v.path()).isEqualTo("PmtInf[1]/CdtTrfTxInf[2]/CdtrAcct/Id/IBAN");
                    assertThat(v.message()).contains("NL91ABNA0417164301");
                });
    }

    @Test
    void creditorIbanRuleReportsAc03WhenAccountIsNotAnIban() {
        assertThat(new CreditorIbanRule().validate(transaction("EUR", null)))
                .extracting(Violation::reasonCode)
                .containsExactly("AC03");
    }

    @Test
    void debtorIbanRuleReportsAc02WithLocation() {
        var pmtInf = new PaymentInformation("PmtInf[1]", "PMT-001", "DE89370400440532013001", null);

        assertThat(new DebtorIbanRule().validate(pmtInf))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("AC02");
                    assertThat(v.path()).isEqualTo("PmtInf[1]/DbtrAcct/Id/IBAN");
                });
    }

    @Test
    void debtorIbanRuleAcceptsValidIban() {
        var pmtInf = new PaymentInformation("PmtInf[1]", "PMT-001", "DE89370400440532013000", null);

        assertThat(new DebtorIbanRule().validate(pmtInf)).isEmpty();
    }

    @Test
    void euroCurrencyRuleAcceptsEur() {
        assertThat(new EuroCurrencyRule().validate(transaction("EUR", "NL91ABNA0417164300"))).isEmpty();
    }

    @Test
    void euroCurrencyRuleReportsAm03ForOtherCurrencies() {
        assertThat(new EuroCurrencyRule().validate(transaction("USD", "NL91ABNA0417164300")))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("AM03");
                    assertThat(v.path()).isEqualTo("PmtInf[1]/CdtTrfTxInf[2]/Amt/InstdAmt/@Ccy");
                    assertThat(v.message()).contains("USD");
                });
    }
}
