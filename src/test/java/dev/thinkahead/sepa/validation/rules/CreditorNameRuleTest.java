// ABOUTME: Unit tests for the SEPA rule that every credit transfer names its creditor (EPC C2PSP IG 2.117).
// ABOUTME: A missing creditor name is reported with RR03 (MissingCreditorNameOrAddress).
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import dev.thinkahead.sepa.validation.pain001.RemittanceInformation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CreditorNameRuleTest {

    private final CreditorNameRule rule = new CreditorNameRule();

    private static CreditTransferTransaction transaction(String creditorName) {
        return new CreditTransferTransaction("PmtInf[1]/CdtTrfTxInf[1]", "E2E-001", new BigDecimal("100.00"), "EUR",
                "NL91ABNA0417164300", null, RemittanceInformation.NONE, creditorName);
    }

    @Test
    void acceptsANamedCreditor() {
        assertThat(rule.validate(transaction("Lieferant AG"))).isEmpty();
    }

    @Test
    void reportsRr03WhenTheCreditorNameIsMissing() {
        assertThat(rule.validate(transaction(null)))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("RR03");
                    assertThat(v.path()).isEqualTo("PmtInf[1]/CdtTrfTxInf[1]/Cdtr/Nm");
                });
    }
}
