// ABOUTME: Validates IBANs in electronic format: SEPA country, country-specific length and mod-97 check digits.
// ABOUTME: Implements the ISO 13616 / ISO 7064 MOD 97-10 algorithm without external libraries.
package dev.thinkahead.sepa.validation.rules;

import java.util.Map;
import java.util.regex.Pattern;

public final class IbanCheck {

    private static final Pattern ELECTRONIC_FORMAT = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}");

    // IBAN lengths of the SEPA scheme countries (EPC409-09); verify against the EPC list on each rulebook update.
    private static final Map<String, Integer> SEPA_IBAN_LENGTHS = Map.ofEntries(
            Map.entry("AD", 24), Map.entry("AT", 20), Map.entry("BE", 16), Map.entry("BG", 22),
            Map.entry("CH", 21), Map.entry("CY", 28), Map.entry("CZ", 24), Map.entry("DE", 22),
            Map.entry("DK", 18), Map.entry("EE", 20), Map.entry("ES", 24), Map.entry("FI", 18),
            Map.entry("FR", 27), Map.entry("GB", 22), Map.entry("GI", 23), Map.entry("GR", 27),
            Map.entry("HR", 21), Map.entry("HU", 28), Map.entry("IE", 22), Map.entry("IS", 26),
            Map.entry("IT", 27), Map.entry("LI", 21), Map.entry("LT", 20), Map.entry("LU", 20),
            Map.entry("LV", 21), Map.entry("MC", 27), Map.entry("MT", 31), Map.entry("NL", 18),
            Map.entry("NO", 15), Map.entry("PL", 28), Map.entry("PT", 25), Map.entry("RO", 24),
            Map.entry("SE", 24), Map.entry("SI", 19), Map.entry("SK", 24), Map.entry("SM", 27),
            Map.entry("VA", 22));

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
