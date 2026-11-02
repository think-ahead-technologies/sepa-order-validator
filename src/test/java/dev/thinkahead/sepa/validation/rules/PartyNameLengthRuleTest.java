// ABOUTME: Unit tests for the SEPA rule limiting party names to 70 characters.
// ABOUTME: Counts characters, not bytes, so names with umlauts are measured as the customer typed them.
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import dev.thinkahead.sepa.validation.pain001.PartyName;
import org.junit.jupiter.api.Test;

class PartyNameLengthRuleTest {

    private final PartyNameLengthRule rule = new PartyNameLengthRule();

    @Test
    void acceptsANameOfSeventyCharacters() {
        String name = "Bäckerei Müller & Söhne Großhandel für Backwaren und Konditoreibedarf ";
        assertThat(name).hasSize(70);

        assertThat(rule.validate(new PartyName("PmtInf[1]/Dbtr/Nm", name))).isEmpty();
    }

    @Test
    void reportsFf01ForANameLongerThanSeventyCharacters() {
        String name = "Gesellschaft für Informationstechnologie und Datenverarbeitung mbH & Co. KG";
        assertThat(name).hasSizeGreaterThan(70);

        assertThat(rule.validate(new PartyName("PmtInf[1]/CdtTrfTxInf[2]/Cdtr/Nm", name)))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("FF01");
                    assertThat(v.path()).isEqualTo("PmtInf[1]/CdtTrfTxInf[2]/Cdtr/Nm");
                    assertThat(v.message())
                            .contains(String.valueOf(name.length()))
                            .contains("70");
                });
    }
}
