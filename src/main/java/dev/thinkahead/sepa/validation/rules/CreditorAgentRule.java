// ABOUTME: Rule: a creditor agent may only be given as BICFI and is left out when the BIC is unknown (EPC IG 2.114).
// ABOUTME: Reports ISO 20022 reason code RC01 (BankIdentifierIncorrect).
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.Agent;
import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import java.util.List;

public class CreditorAgentRule implements ValidationRule<CreditTransferTransaction> {

    @Override
    public List<Violation> validate(CreditTransferTransaction transaction) {
        Agent agent = transaction.creditorAgent();
        if (agent == null || (agent.bic() != null && !agent.furtherDetails())) {
            return List.of();
        }
        return List.of(new Violation(
                "RC01",
                transaction.path() + "/CdtrAgt",
                "Creditor agent may only contain BICFI; leave it out when the BIC is not known"));
    }
}
