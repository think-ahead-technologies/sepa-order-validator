// ABOUTME: A payment information id (PmtInfId) of a pain.001 message together with its location in the file.
// ABOUTME: The path names the element, e.g. "PmtInf[2]/PmtInfId", the location of a DU02 violation.
package dev.thinkahead.sepa.validation.pain001;

public record PaymentInformationReference(String id, String path) {}
