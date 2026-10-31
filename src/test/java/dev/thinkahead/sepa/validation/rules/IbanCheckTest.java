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
            "AL47212110090000000235698741", // Albania
            "MD24AG000225100013104168",     // Moldova
            "ME25505000012345678951",       // Montenegro
            "MK07250120000058984",          // North Macedonia
            "RS35260005601001611379"        // Serbia
    })
    void acceptsIbansOfCountriesThatJoinedSepaIn2025(String iban) {
        assertThat(IbanCheck.isValid(iban)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "AL4721211009000000023569874",  // too short for AL
            "MK072501200000589840",         // too long for MK
            "RS35260005601001611378"        // wrong check digits
    })
    void checksLengthAndCheckDigitsOfCountriesThatJoinedSepaIn2025(String iban) {
        assertThat(IbanCheck.isValid(iban)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "NL91abna0417164300",          // BBAN letters in lower case: allowed by the pain.001 schema
            "FR1420041010050500013m02606", // single lower case letter in the BBAN
            "nl91ABNA0417164300"           // lower case country code
    })
    void rejectsLowerCaseLettersAlthoughTheSchemaAllowsThemInTheBban(String iban) {
        assertThat(IbanCheck.isValid(iban)).isFalse();
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
