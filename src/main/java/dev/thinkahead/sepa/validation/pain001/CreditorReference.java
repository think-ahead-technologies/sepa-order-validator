// ABOUTME: Creditor reference (Strd/CdtrRefInf) of a credit transfer's structured remittance information.
// ABOUTME: typeCode is the Tp/CdOrPrtry/Cd value; it is null when the type is absent or proprietary.
package dev.thinkahead.sepa.validation.pain001;

public record CreditorReference(boolean typePresent, String typeCode, String reference) {
}
