// ABOUTME: One payment type information block (PmtTpInf) on payment information or transaction level.
// ABOUTME: Carries the service level code and the local instrument as code or proprietary value; null when absent.
package dev.thinkahead.sepa.validation.pain001;

public record PaymentTypeInformation(
        String path, String localInstrumentCode, String localInstrumentProprietary, String serviceLevelCode) {}
