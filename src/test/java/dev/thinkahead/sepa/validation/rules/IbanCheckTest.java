// ABOUTME: Unit tests for IBAN structure and ISO 7064 mod-97 check digit validation.
// ABOUTME: Uses real published example IBANs from several SEPA countries.
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IbanCheckTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "DE89370400440532013000",
            "NL91ABNA0417164300",
            "FR1420041010050500013M02606",
            "AT611904300234573201",
            "GB82WEST12345698765432",
            "BE68539007547034",
            "IT60X0542811101000000123456",
            "ES9121000418450200051332"
    })
    void acceptsValidIbans(String iban) {
        assertThat(IbanCheck.isValid(iban)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DE89370400440532013001",   // wrong check digits
            "DE8937040044053201300",    // too short for DE
            "DE893704004405320130000",  // too long for DE
            "XX89370400440532013000",   // unknown country
            "de89370400440532013000",   // lower case
            "DE89 3704 0044 0532 0130 00" // paper format with spaces
    })
    void rejectsInvalidIbans(String iban) {
        assertThat(IbanCheck.isValid(iban)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsMissingIban(String iban) {
        assertThat(IbanCheck.isValid(iban)).isFalse();
    }
}
