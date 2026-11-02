// ABOUTME: Shape of a credit transfer's remittance information (RmtInf): occurrences of Ustrd and Strd.
// ABOUTME: Structured length counts tags, attributes and data inside <Strd>, as the EPC 140-character limit does.
package dev.thinkahead.sepa.validation.pain001;

public record RemittanceInformation(
        int unstructuredCount, int structuredCount, int longestStructuredLength, CreditorReference creditorReference) {

    public static final RemittanceInformation NONE = new RemittanceInformation(0, 0, 0, null);
}
