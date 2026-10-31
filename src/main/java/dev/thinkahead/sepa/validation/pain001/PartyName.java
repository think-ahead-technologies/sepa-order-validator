// ABOUTME: Name of a party (initiating party, debtor, creditor or an ultimate party) in a pain.001 message.
// ABOUTME: The path locates the name in the file, e.g. "PmtInf[1]/CdtTrfTxInf[2]/Cdtr/Nm".
package dev.thinkahead.sepa.validation.pain001;

public record PartyName(String path, String name) {
}
