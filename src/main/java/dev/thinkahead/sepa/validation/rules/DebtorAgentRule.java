// ABOUTME: Rule: the debtor agent is identified either by BICFI or by Othr/Id (EPC C2PSP IG 2.54).
// ABOUTME: Reports ISO 20022 reason code RC01 (BankIdentifierIncorrect).
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.Agent;
import dev.thinkahead.sepa.validation.pain001.PaymentInformation;
import java.util.List;

public class DebtorAgentRule implements ValidationRule<PaymentInformation> {

    @Override
    public List<Violation> validate(PaymentInformation paymentInformation) {
        Agent agent = paymentInformation.debtorAgent();
        if (agent == null || agent.bic() != null || agent.otherIdentification() != null) {
            return List.of();
        }
        return List.of(new Violation(
                "RC01",
                paymentInformation.path() + "/DbtrAgt/FinInstnId",
                "Debtor agent must be identified by BICFI or Othr/Id"));
    }
}
