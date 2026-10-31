// ABOUTME: One payment type information block (PmtTpInf) on payment information or transaction level.
// ABOUTME: Carries the local instrument as code (e.g. INST) or proprietary value; both are null when absent.
package dev.thinkahead.sepa.validation.pain001;

public record PaymentTypeInformation(String path, String localInstrumentCode, String localInstrumentProprietary) {
}
