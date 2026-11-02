// ABOUTME: The party a postal address belongs to: a payment information block's debtor or a transaction's creditor.
// ABOUTME: Decides the reason code of an address violation (RR02 for the debtor, RR03 for the creditor).
package dev.thinkahead.sepa.validation.pain001;

public enum Party {
    DEBTOR,
    CREDITOR
}
