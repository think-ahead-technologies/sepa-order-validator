// ABOUTME: Unit tests for the DK character set rule (DFÜ-Abkommen Anlage 3 v3.9, 2.1 and 2.3.1; EPC C2PSP IG 1.4).
// ABOUTME: Text allows basic Latin plus umlauts, ß, &, *, $ and %; identifiers only basic Latin and no stray slashes.
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import dev.thinkahead.sepa.validation.pain001.ElementText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CharacterSetRuleTest {

    private static final String CREDITOR_NAME = "PmtInf[1]/CdtTrfTxInf[2]/Cdtr/Nm";
    private static final String END_TO_END_ID = "PmtInf[1]/CdtTrfTxInf[2]/PmtId/EndToEndId";

    private final CharacterSetRule rule = new CharacterSetRule();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Muster GmbH",
                "abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789 /-?:().,'+",
                "Bäckerei Müller & Söhne, Großhandel",
                "ÄÖÜ äöü ß & * $ %"
            })
    void acceptsTheDkCharacterSetInText(String text) {
        assertThat(rule.validate(new ElementText(CREDITOR_NAME, text))).isEmpty();
    }

    @Test
    void reportsCh16WithCodePointAndPositionForACharacterOutsideTheDkSet() {
        assertThat(rule.validate(new ElementText(CREDITOR_NAME, "Société Générale")))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("CH16");
                    assertThat(v.path()).isEqualTo(CREDITOR_NAME);
                    assertThat(v.message())
                            .contains("U+00E9")
                            .contains("position 5")
                            .doesNotContain("Soci");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"Wrocław", "Euro €", "50 @ Lager", "A_B", "Kunde \"Nord\"", "x<y", "Zeile\tzwei", "😀"})
    void reportsCh16ForOtherCharacters(String text) {
        assertThat(rule.validate(new ElementText(CREDITOR_NAME, text)))
                .singleElement()
                .satisfies(v -> assertThat(v.reasonCode()).isEqualTo("CH16"));
    }

    @Test
    void reportsACharacterOutsideTheBasicMultilingualPlaneAsOneCodePoint() {
        assertThat(rule.validate(new ElementText(CREDITOR_NAME, "AB😀")))
                .singleElement()
                .satisfies(v -> assertThat(v.message()).contains("U+1F600").contains("position 3"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"E2E-2026/10/31-001", "MSG 1 (Test) +?:.,'"})
    void acceptsBasicLatinIdentifiers(String identifier) {
        assertThat(rule.validate(new ElementText(END_TO_END_ID, identifier))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "GrpHdr/MsgId",
                "PmtInf[1]/PmtInfId",
                "PmtInf[1]/CdtTrfTxInf[2]/PmtId/InstrId",
                "PmtInf[1]/CdtTrfTxInf[2]/PmtId/EndToEndId"
            })
    void reportsCh16ForDkAdditionalCharactersInIdentifiers(String path) {
        assertThat(rule.validate(new ElementText(path, "Rechnung-Müller")))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("CH16");
                    assertThat(v.path()).isEqualTo(path);
                    assertThat(v.message()).contains("U+00FC");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"/E2E-001", "E2E-001/", "E2E//001"})
    void reportsCh16ForStraySlashesInIdentifiers(String identifier) {
        assertThat(rule.validate(new ElementText(END_TO_END_ID, identifier)))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("CH16");
                    assertThat(v.message()).contains("/");
                });
    }

    @Test
    void allowsSlashesAnywhereInText() {
        assertThat(rule.validate(new ElementText("PmtInf[1]/CdtTrfTxInf[1]/RmtInf/Ustrd", "/INV/2026//0815/")))
                .isEmpty();
    }
}
