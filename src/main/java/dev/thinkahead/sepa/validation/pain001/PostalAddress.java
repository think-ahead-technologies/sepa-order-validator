// ABOUTME: Shape of a debtor or creditor postal address (PstlAdr): its location, party and the elements it contains.
// ABOUTME: townName and country tell whether TwnNm and Ctry are present; addressLineCount counts the AdrLine elements.
package dev.thinkahead.sepa.validation.pain001;

public record PostalAddress(String path, Party party, int addressLineCount, boolean townName, boolean country) {}
