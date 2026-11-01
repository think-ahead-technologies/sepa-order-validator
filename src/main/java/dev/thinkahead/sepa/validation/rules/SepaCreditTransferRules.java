// ABOUTME: Rule set for SEPA credit transfers (pain.001), applied while the file is streamed.
// ABOUTME: One instance per file; it collects every violation from every rule.
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.intake.Submission;
import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import dev.thinkahead.sepa.validation.pain001.ElementText;
import dev.thinkahead.sepa.validation.pain001.Pain001Handler;
import dev.thinkahead.sepa.validation.pain001.PartyName;
import dev.thinkahead.sepa.validation.pain001.PaymentInformation;
import dev.thinkahead.sepa.validation.pain001.PaymentTypeInformation;
import dev.thinkahead.sepa.validation.pain001.PostalAddress;
import dev.thinkahead.sepa.validation.pain001.Totals;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class SepaCreditTransferRules implements Pain001Handler {

    private static final List<ValidationRule<CreditTransferTransaction>> TRANSACTION_RULES =
            List.of(new CreditorIbanRule(), new EuroCurrencyRule(), new AmountRule(), new CreditorAgentRule(),
                    new RemittanceInformationRule(), new CreditorReferenceRule(), new CreditorNameRule());
    private static final List<ValidationRule<Totals>> TOTALS_RULES =
            List.of(new NumberOfTransactionsRule(), new ControlSumRule());
    private static final List<ValidationRule<PartyName>> PARTY_NAME_RULES = List.of(new PartyNameLengthRule());
    private static final List<ValidationRule<PostalAddress>> POSTAL_ADDRESS_RULES = List.of(new PostalAddressRule());
    private static final List<ValidationRule<ElementText>> ELEMENT_TEXT_RULES = List.of(new CharacterSetRule());

    private final List<ValidationRule<PaymentInformation>> paymentInformationRules;
    private final List<ValidationRule<PaymentTypeInformation>> paymentTypeRules =
            List.of(new ServiceLevelRule(), new LocalInstrumentRule(), new PaymentTypeInformationLevelRule());
    private final List<Violation> violations = new ArrayList<>();
    private final int maxReportedViolations;
    private long unreportedViolations;

    /**
     * @param maxReportedViolations violations beyond this number are only counted and summarised in one NARR entry
     * @param today the bank's calendar date, against which requested execution dates are checked
     */
    public SepaCreditTransferRules(int maxReportedViolations, LocalDate today) {
        this(maxReportedViolations, today, Submission.REGULAR);
    }

    /**
     * @param submission how the file was submitted, checked against its content
     */
    public SepaCreditTransferRules(int maxReportedViolations, LocalDate today, Submission submission) {
        this.maxReportedViolations = maxReportedViolations;
        this.paymentInformationRules = List.of(new DebtorIbanRule(), new DebtorAgentRule(),
                new UniquePaymentInformationIdRule(), new ExecutionDateRule(today), new SingleSchemeRule(),
                new SubmissionRule(submission));
    }

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

    @Override
    public void partyName(PartyName partyName) {
        apply(PARTY_NAME_RULES, partyName);
    }

    @Override
    public void postalAddress(PostalAddress postalAddress) {
        apply(POSTAL_ADDRESS_RULES, postalAddress);
    }

    @Override
    public void elementText(ElementText elementText) {
        apply(ELEMENT_TEXT_RULES, elementText);
    }

    @Override
    public void paymentTypeInformation(PaymentTypeInformation paymentTypeInformation) {
        apply(paymentTypeRules, paymentTypeInformation);
    }

    public List<Violation> violations() {
        if (unreportedViolations == 0) {
            return List.copyOf(violations);
        }
        var reported = new ArrayList<>(violations);
        reported.add(new Violation("NARR", "Document",
                "... and " + unreportedViolations + " more violations not listed"));
        return List.copyOf(reported);
    }

    private <T> void apply(List<ValidationRule<T>> rules, T subject) {
        for (ValidationRule<T> rule : rules) {
            for (Violation violation : rule.validate(subject)) {
                if (violations.size() < maxReportedViolations) {
                    violations.add(violation);
                } else {
                    unreportedViolations++;
                }
            }
        }
    }
}
