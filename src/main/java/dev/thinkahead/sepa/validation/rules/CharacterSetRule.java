// ABOUTME: Rule: text uses the DK character set (DFÜ-Abkommen Anlage 3 v3.9, 2.1), identifiers basic Latin (2.3.1).
// ABOUTME: Identifiers must not start or end with '/' or contain '//' (EPC C2PSP IG 1.4); reports CH16.
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.ElementText;
import java.util.List;

public class CharacterSetRule implements ValidationRule<ElementText> {

    private static final String BASIC_LATIN =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/-?:().,'+ ";

    // Characters every German bank must accept beyond the EPC basic Latin set; the bank does not convert them.
    private static final String DK_ADDITIONAL = "ÄÖÜäöüß&*$%";

    private static final List<String> IDENTIFIERS = List.of("/MsgId", "/PmtInfId", "/InstrId", "/EndToEndId");

    @Override
    public List<Violation> validate(ElementText text) {
        boolean identifier = IDENTIFIERS.stream().anyMatch(text.path()::endsWith);
        String allowed = identifier ? BASIC_LATIN : BASIC_LATIN + DK_ADDITIONAL;
        int[] codePoints = text.value().codePoints().toArray();
        for (int i = 0; i < codePoints.length; i++) {
            if (allowed.indexOf(codePoints[i]) < 0) {
                String where =
                        identifier ? " in identifiers, only the basic Latin set is" : ", outside the DK character set";
                return List.of(new Violation(
                        "CH16",
                        text.path(),
                        "Character " + String.format("U+%04X", codePoints[i]) + " at position " + (i + 1)
                                + " is not allowed" + where));
            }
        }
        if (identifier && (text.value().startsWith("/") || text.value().endsWith("/"))) {
            return List.of(new Violation("CH16", text.path(), "Identifier must not start or end with '/'"));
        }
        if (identifier && text.value().contains("//")) {
            return List.of(new Violation("CH16", text.path(), "Identifier must not contain '//'"));
        }
        return List.of();
    }
}
