// ABOUTME: Shape of a debtor or creditor postal address (PstlAdr) and the requested execution date it is used for.
// ABOUTME: otherElements is true when the address has any element besides AdrLine and Ctry, e.g. TwnNm or StrtNm.
package dev.thinkahead.sepa.validation.pain001;

import java.time.LocalDate;

public record PostalAddress(
        String path,
        Party party,
        LocalDate requestedExecutionDate,
        int addressLineCount,
        boolean townName,
        boolean country,
        boolean otherElements) {
}
