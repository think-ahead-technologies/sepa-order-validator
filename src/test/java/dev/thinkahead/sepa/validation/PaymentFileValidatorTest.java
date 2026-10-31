// ABOUTME: Unit tests for the end-to-end validation of a single payment file's content.
// ABOUTME: Covers acceptance, rule violations, schema failures and unsupported formats.
package dev.thinkahead.sepa.validation;

import static dev.thinkahead.sepa.Fixtures.VALID_PAIN_001;
import static dev.thinkahead.sepa.Fixtures.bytes;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentFileValidatorTest {

    private final PaymentFileValidator validator = new PaymentFileValidator();

    @Test
    void acceptsValidCreditTransferFile() {
        ValidationResult result = validator.validate(bytes(VALID_PAIN_001));

        assertThat(result.accepted()).isTrue();
        assertThat(result.violations()).isEmpty();
        assertThat(result.originalMessageId()).isEqualTo("MSG-2026-10-31-001");
        assertThat(result.originalMessageNameId()).isEqualTo("pain.001.001.09");
    }

    @Test
    void collectsAllRuleViolationsInsteadOfStoppingAtTheFirst() {
        var xml = VALID_PAIN_001
                .replace("NL91ABNA0417164300", "NL91ABNA0417164301")
                .replace("Ccy=\"EUR\">50.50", "Ccy=\"USD\">50.50")
                .replace("<NbOfTxs>2</NbOfTxs>\n      <CtrlSum>150.50</CtrlSum>\n      <InitgPty>",
                        "<NbOfTxs>3</NbOfTxs>\n      <CtrlSum>150.50</CtrlSum>\n      <InitgPty>");

        ValidationResult result = validator.validate(bytes(xml));

        assertThat(result.accepted()).isFalse();
        assertThat(result.originalMessageId()).isEqualTo("MSG-2026-10-31-001");
        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("AC03", "PmtInf[1]/CdtTrfTxInf[1]/CdtrAcct/Id/IBAN"),
                        org.assertj.core.groups.Tuple.tuple("AM03", "PmtInf[1]/CdtTrfTxInf[2]/Amt/InstdAmt/@Ccy"),
                        org.assertj.core.groups.Tuple.tuple("AM18", "GrpHdr/NbOfTxs"));
    }

    @Test
    void rejectsSchemaInvalidFileWithFf01AndSkipsContentRules() {
        var xml = VALID_PAIN_001
                .replace("<PmtMtd>TRF</PmtMtd>", "<PmtMtd>XYZ</PmtMtd>")
                .replace("NL91ABNA0417164300", "NL91ABNA0417164301");

        ValidationResult result = validator.validate(bytes(xml));

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations()).isNotEmpty().allSatisfy(v -> assertThat(v.reasonCode()).isEqualTo("FF01"));
        assertThat(result.originalMessageNameId()).isEqualTo("pain.001.001.09");
    }

    @Test
    void rejectsUnsupportedFormatWithFf01() {
        var xml = VALID_PAIN_001.replace("pain.001.001.09", "pain.001.001.99");

        ValidationResult result = validator.validate(bytes(xml));

        assertThat(result.accepted()).isFalse();
        assertThat(result.originalMessageId()).isNull();
        assertThat(result.originalMessageNameId()).isNull();
        assertThat(result.violations()).singleElement()
                .satisfies(v -> assertThat(v.reasonCode()).isEqualTo("FF01"));
    }
}
