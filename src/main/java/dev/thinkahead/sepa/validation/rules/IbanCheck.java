// ABOUTME: Validates IBANs in electronic format: SEPA country, country-specific length and mod-97 check digits.
// ABOUTME: Implements the ISO 13616 / ISO 7064 MOD 97-10 algorithm without external libraries.
package dev.thinkahead.sepa.validation.rules;

import java.util.Map;
import java.util.regex.Pattern;

public final class IbanCheck {

    // ISO 13616 electronic format: upper case only. The pain.001 schema also allows lower case letters in the BBAN,
    // but such an IBAN is rejected with AC03 on purpose: the bank forwards the customer's order unchanged and does
    // not normalise it, so the customer is asked to send the IBAN in electronic format.
    private static final Pattern ELECTRONIC_FORMAT = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}");

    // IBAN country codes of the SEPA scheme countries per EPC409-09 v8.0 (24 December 2025), with lengths from the
    // SWIFT IBAN registry. Territories use the codes listed there (e.g. FR, FI, GB). Verify on each EPC409 update.
    private static final Map<String, Integer> SEPA_IBAN_LENGTHS = Map.ofEntries(
            Map.entry("AD", 24), Map.entry("AL", 28), Map.entry("AT", 20), Map.entry("BE", 16),
            Map.entry("BG", 22), Map.entry("CH", 21), Map.entry("CY", 28), Map.entry("CZ", 24),
            Map.entry("DE", 22), Map.entry("DK", 18), Map.entry("EE", 20), Map.entry("ES", 24),
            Map.entry("FI", 18), Map.entry("FR", 27), Map.entry("GB", 22), Map.entry("GI", 23),
            Map.entry("GR", 27), Map.entry("HR", 21), Map.entry("HU", 28), Map.entry("IE", 22),
            Map.entry("IS", 26), Map.entry("IT", 27), Map.entry("LI", 21), Map.entry("LT", 20),
            Map.entry("LU", 20), Map.entry("LV", 21), Map.entry("MC", 27), Map.entry("MD", 24),
            Map.entry("ME", 22), Map.entry("MK", 19), Map.entry("MT", 31), Map.entry("NL", 18),
            Map.entry("NO", 15), Map.entry("PL", 28), Map.entry("PT", 25), Map.entry("RO", 24),
            Map.entry("RS", 22), Map.entry("SE", 24), Map.entry("SI", 19), Map.entry("SK", 24),
            Map.entry("SM", 27), Map.entry("VA", 22));

    private IbanCheck() {
    }

    public static boolean isValid(String iban) {
        if (iban == null || !ELECTRONIC_FORMAT.matcher(iban).matches()) {
            return false;
        }
        Integer expectedLength = SEPA_IBAN_LENGTHS.get(iban.substring(0, 2));
        return expectedLength != null
                && iban.length() == expectedLength
                && mod97(iban.substring(4) + iban.substring(0, 4)) == 1;
    }

    private static int mod97(String rearranged) {
        int remainder = 0;
        for (char c : rearranged.toCharArray()) {
            if (Character.isDigit(c)) {
                remainder = (remainder * 10 + (c - '0')) % 97;
            } else {
                remainder = (remainder * 100 + (c - 'A' + 10)) % 97;
            }
        }
        return remainder;
    }
}
