// ABOUTME: Masks IBANs in every string value of the structured (JSON) log output, including messages and stack traces.
// ABOUTME: Safety net only: code must still never log names or other values taken from a payment file (spec
// observability).
package dev.thinkahead.sepa;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.json.JsonWriter.Members;
import org.springframework.boot.json.JsonWriter.ValueProcessor;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Configured as {@code logging.structured.json.customizer}. An IBAN keeps its country code, check digits and last
 * four characters, which is enough to tell accounts apart in an incident without exposing the account number.
 */
public class LogMasking implements StructuredLoggingJsonMembersCustomizer<Object> {

    private static final int VISIBLE_AT_EACH_END = 4;

    // Electronic format (15 to 34 characters) or paper format in groups of four separated by single spaces.
    private static final Pattern IBAN = Pattern.compile(
            "\\b[A-Za-z]{2}[0-9]{2}(?:[A-Za-z0-9]{11,30}|(?: [A-Za-z0-9]{4}){2,7}(?: [A-Za-z0-9]{1,3})?)\\b");

    @Override
    public void customize(Members<Object> members) {
        members.applyingValueProcessor(ValueProcessor.of(String.class, LogMasking::mask));
    }

    static String mask(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = IBAN.matcher(value);
        return matcher.replaceAll(match -> Matcher.quoteReplacement(maskIban(match.group())));
    }

    private static String maskIban(String iban) {
        long characters = iban.chars().filter(Character::isLetterOrDigit).count();
        var masked = new StringBuilder(iban.length());
        int position = 0;
        for (char c : iban.toCharArray()) {
            if (c == ' ') {
                masked.append(c);
                continue;
            }
            boolean visible = position < VISIBLE_AT_EACH_END || position >= characters - VISIBLE_AT_EACH_END;
            masked.append(visible ? c : '*');
            position++;
        }
        return masked.toString();
    }
}
