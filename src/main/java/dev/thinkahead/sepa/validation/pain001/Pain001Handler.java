// ABOUTME: Callback interface receiving pain.001 content piece by piece while the file is streamed.
// ABOUTME: Lets rules run per transaction without holding the whole file in memory (ADR-0004).
package dev.thinkahead.sepa.validation.pain001;

public interface Pain001Handler {

    void paymentInformation(PaymentInformation paymentInformation);

    void transaction(CreditTransferTransaction transaction);

    void totals(Totals totals);
}
