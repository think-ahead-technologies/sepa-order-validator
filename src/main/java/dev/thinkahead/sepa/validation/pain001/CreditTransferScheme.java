// ABOUTME: Scheme of a payment information block's transactions: SEPA credit transfer, SEPA Instant, or a mix of both.
// ABOUTME: A transaction is SEPA Instant when its effective local instrument is INST (DK Anlage 3 v3.7, 2.2.4.1).
package dev.thinkahead.sepa.validation.pain001;

public enum CreditTransferScheme {
    SCT,
    SCT_INST,
    MIXED
}
