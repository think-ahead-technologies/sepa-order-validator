// ABOUTME: Unit tests for the SEPA debtor and creditor agent rules (EPC C2PSP IG 2.54 and 2.114).
// ABOUTME: The debtor agent needs a BIC or Othr/Id; a creditor agent may only carry a BIC. Both report RC01.
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import dev.thinkahead.sepa.validation.pain001.Agent;
import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import dev.thinkahead.sepa.validation.pain001.PaymentInformation;
import dev.thinkahead.sepa.validation.pain001.RemittanceInformation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AgentRulesTest {

    private final DebtorAgentRule debtorAgentRule = new DebtorAgentRule();
    private final CreditorAgentRule creditorAgentRule = new CreditorAgentRule();

    private static PaymentInformation paymentInformation(Agent debtorAgent) {
        return new PaymentInformation("PmtInf[1]", "PMT-001", "DE89370400440532013000", debtorAgent);
    }

    private static CreditTransferTransaction transaction(Agent creditorAgent) {
        return new CreditTransferTransaction("PmtInf[1]/CdtTrfTxInf[2]", "E2E-002", new BigDecimal("50.50"), "EUR",
                "NL91ABNA0417164300", creditorAgent, RemittanceInformation.NONE);
    }

    @Test
    void acceptsADebtorAgentIdentifiedByBic() {
        assertThat(debtorAgentRule.validate(paymentInformation(new Agent("COBADEFFXXX", null, false)))).isEmpty();
    }

    @Test
    void acceptsADebtorAgentIdentifiedByOtherIdentification() {
        assertThat(debtorAgentRule.validate(paymentInformation(new Agent(null, "NOTPROVIDED", true)))).isEmpty();
    }

    @Test
    void reportsRc01ForADebtorAgentWithoutBicOrOtherIdentification() {
        assertThat(debtorAgentRule.validate(paymentInformation(new Agent(null, null, true))))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("RC01");
                    assertThat(v.path()).isEqualTo("PmtInf[1]/DbtrAgt/FinInstnId");
                });
    }

    @Test
    void acceptsATransactionWithoutCreditorAgent() {
        assertThat(creditorAgentRule.validate(transaction(null))).isEmpty();
    }

    @Test
    void acceptsACreditorAgentIdentifiedOnlyByBic() {
        assertThat(creditorAgentRule.validate(transaction(new Agent("ABNANL2A", null, false)))).isEmpty();
    }

    @Test
    void reportsRc01ForACreditorAgentWithoutBic() {
        assertThat(creditorAgentRule.validate(transaction(new Agent(null, null, true))))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("RC01");
                    assertThat(v.path()).isEqualTo("PmtInf[1]/CdtTrfTxInf[2]/CdtrAgt");
                    assertThat(v.message()).contains("BICFI");
                });
    }

    @Test
    void reportsRc01ForACreditorAgentWithDetailsBesidesTheBic() {
        assertThat(creditorAgentRule.validate(transaction(new Agent("ABNANL2A", null, true))))
                .singleElement()
                .satisfies(v -> assertThat(v.reasonCode()).isEqualTo("RC01"));
    }
}
