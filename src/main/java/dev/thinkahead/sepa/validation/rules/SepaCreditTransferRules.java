// ABOUTME: Rule set for SEPA credit transfers (pain.001), applied while the file is streamed.
// ABOUTME: One instance per file; it collects every violation from every rule.
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import dev.thinkahead.sepa.validation.pain001.Pain001Handler;
import dev.thinkahead.sepa.validation.pain001.PaymentInformation;
import dev.thinkahead.sepa.validation.pain001.Totals;
import java.util.ArrayList;
import java.util.List;

public final class SepaCreditTransferRules implements Pain001Handler {

    private static final List<ValidationRule<CreditTransferTransaction>> TRANSACTION_RULES =
            List.of(new CreditorIbanRule(), new EuroCurrencyRule());
    private static final List<ValidationRule<Totals>> TOTALS_RULES =
            List.of(new NumberOfTransactionsRule(), new ControlSumRule());

    private final List<ValidationRule<PaymentInformation>> paymentInformationRules =
            List.of(new DebtorIbanRule(), new UniquePaymentInformationIdRule());
    private final List<Violation> violations = new ArrayList<>();

    @Override
    public void paymentInformation(PaymentInformation paymentInformation) {
        apply(paymentInformationRules, paymentInformation);
    }

    @Override
    public void transaction(CreditTransferTransaction transaction) {
        apply(TRANSACTION_RULES, transaction);
    }

    @Override
    public void totals(Totals totals) {
        apply(TOTALS_RULES, totals);
    }

    public List<Violation> violations() {
        return List.copyOf(violations);
    }

    private <T> void apply(List<ValidationRule<T>> rules, T subject) {
        rules.forEach(rule -> violations.addAll(rule.validate(subject)));
    }
}
