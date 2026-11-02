// ABOUTME: Identification of a debtor or creditor agent (the customer's or the beneficiary's PSP) in a pain.001.
// ABOUTME: Further details are any agent elements besides FinInstnId/BICFI, e.g. Othr, ClrSysMmbId or BrnchId.
package dev.thinkahead.sepa.validation.pain001;

public record Agent(String bic, String otherIdentification, boolean furtherDetails) {}
