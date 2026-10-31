// ABOUTME: Text content of one leaf element of a pain.001 message, trimmed, with its location in the file.
// ABOUTME: The path carries indices of payment information and transactions, e.g. "PmtInf[1]/CdtTrfTxInf[2]/Cdtr/Nm".
package dev.thinkahead.sepa.validation.pain001;

public record ElementText(String path, String value) {
}
