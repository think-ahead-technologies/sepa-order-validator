// ABOUTME: Unit tests for LogMasking: IBANs in log values are masked down to country, check digits and last four.
// ABOUTME: Covers electronic and paper format, several IBANs per value, and values that must stay untouched.
package dev.thinkahead.sepa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LogMaskingTest {

    @Test
    void masksAnIbanInElectronicFormat() {
        assertThat(LogMasking.mask("Invalid IBAN DE89370400440532013000 in transaction 1"))
                .isEqualTo("Invalid IBAN DE89**************3000 in transaction 1");
    }

    @Test
    void masksTheShortestAndTheLongestSepaIbans() {
        assertThat(LogMasking.mask("NO9386011117947")).isEqualTo("NO93*******7947");
        assertThat(LogMasking.mask("MT84MALT011000012345MTLCAST001S")).isEqualTo("MT84***********************001S");
    }

    @Test
    void masksAnIbanInPaperFormatWithoutRevealingItsGroups() {
        assertThat(LogMasking.mask("IBAN: DE89 3704 0044 0532 0130 00."))
                .isEqualTo("IBAN: DE89 **** **** **** **30 00.");
    }

    @Test
    void masksALowerCaseIban() {
        assertThat(LogMasking.mask("nl91abna0417164300")).isEqualTo("nl91**********4300");
    }

    @Test
    void masksEveryIbanInAValue() {
        assertThat(LogMasking.mask("from AT611904300234573201 to NL91ABNA0417164300"))
                .isEqualTo("from AT61************3201 to NL91**********4300");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Received payment file e2e-6f1c1f0e-8a39-4d6b-9a52-2b3c4d5e6f70.xml from sftp:sepa",
            "sha256 5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef",
            "Validated payment file: rejected with reason codes [AC03, AM03]",
            "Message id MSG-2026-10-31-001 accepted",
            "Instance 6da27fae-c9e0-414f-922c-739cfcdb295b became leader for sftp-intake",
            "at dev.thinkahead.sepa.intake.SftpChannel.writeOutbound(SftpChannel.java:113)"
    })
    void leavesValuesWithoutIbansUntouched(String value) {
        assertThat(LogMasking.mask(value)).isEqualTo(value);
    }
}
