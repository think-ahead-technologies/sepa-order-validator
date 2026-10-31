// ABOUTME: Unit tests for the end-to-end validation of a single payment file's content.
// ABOUTME: Covers acceptance, rule violations, schema failures, unsupported formats and the validation deadline.
package dev.thinkahead.sepa.validation;

import static dev.thinkahead.sepa.Fixtures.SAMPLE_MESSAGE_ID;
import static dev.thinkahead.sepa.Fixtures.VALID_PAIN_001;
import static dev.thinkahead.sepa.Fixtures.bytes;
import static dev.thinkahead.sepa.Fixtures.withCreditorNameAndAddressViolations;
import static dev.thinkahead.sepa.Fixtures.withSepaFieldRuleViolations;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PaymentFileValidatorTest {

    private static final Instant NO_DEADLINE = Instant.now().plus(Duration.ofHours(1));

    private static final Clock FRIDAY_30_OCTOBER_2026 = clockAt("2026-10-30T09:00:00Z");

    private final PaymentFileValidator validator = new PaymentFileValidator(FRIDAY_30_OCTOBER_2026);

    private static Clock clockAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void rejectsCharactersOutsideTheDkCharacterSetWithCh16() {
        ValidationResult result = validator.validate(
                bytes(dev.thinkahead.sepa.Fixtures.withCharacterSetViolations(SAMPLE_MESSAGE_ID)), NO_DEADLINE);

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactlyInAnyOrder(
                        tuple("CH16", "PmtInf[1]/CdtTrfTxInf[1]/PmtId/EndToEndId"),
                        tuple("CH16", "PmtInf[1]/CdtTrfTxInf[2]/Cdtr/Nm"));
    }

    @Test
    void rejectsAnExecutionDateInThePastWithDt01() {
        var xml = VALID_PAIN_001.replace("<Dt>2026-11-02</Dt>", "<Dt>2026-10-29</Dt>");

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("DT01", "PmtInf[1]/ReqdExctnDt"));
    }

    @Test
    void rejectsAnExecutionDateOnAWeekendWithDt01() {
        var xml = VALID_PAIN_001.replace("<Dt>2026-11-02</Dt>", "<Dt>2026-11-07</Dt>");

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("DT01", "PmtInf[1]/ReqdExctnDt"));
    }

    @Test
    void judgesThePastByTheCalendarDateInFrankfurt() {
        // 2 November 2026, 23:30 in Frankfurt, but already 3 November there at 00:30 (UTC+1).
        var lateEvening = new PaymentFileValidator(clockAt("2026-11-02T22:30:00Z"));
        var afterMidnight = new PaymentFileValidator(clockAt("2026-11-02T23:30:00Z"));

        assertThat(lateEvening.validate(bytes(VALID_PAIN_001), NO_DEADLINE).violations()).isEmpty();
        assertThat(afterMidnight.validate(bytes(VALID_PAIN_001), NO_DEADLINE).violations())
                .extracting(Violation::reasonCode).containsExactly("DT01");
    }

    @Test
    void rejectsAFileWhoseValidationRunsPastTheDeadlineWithFf01() {
        ValidationResult result = validator.validate(bytes(VALID_PAIN_001), Instant.now().minusSeconds(1));

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.reasonCode()).isEqualTo("FF01");
            assertThat(violation.message()).contains("time limit");
        });
    }

    @Test
    void acceptsValidCreditTransferFile() {
        ValidationResult result = validator.validate(bytes(VALID_PAIN_001), NO_DEADLINE);

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

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

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
    void rejectsSchemaValidFileBreakingSepaFieldRules() {
        ValidationResult result = validator.validate(
                bytes(withSepaFieldRuleViolations(SAMPLE_MESSAGE_ID)), NO_DEADLINE);

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactlyInAnyOrder(
                        tuple("AM01", "PmtInf[1]/CdtTrfTxInf[1]/Amt/InstdAmt"),
                        tuple("RC01", "PmtInf[1]/CdtTrfTxInf[1]/CdtrAgt"),
                        tuple("AM12", "PmtInf[1]/CdtTrfTxInf[2]/Amt/InstdAmt"),
                        tuple("FF01", "PmtInf[1]/CdtTrfTxInf[2]/Cdtr/Nm"),
                        tuple("FF01", "PmtInf[1]/CdtTrfTxInf[2]/RmtInf"));
    }

    @Test
    void rejectsMissingCreditorNameAndUnstructuredAddressAfterTheCutoverWithRr03() {
        ValidationResult result = validator.validate(
                bytes(withCreditorNameAndAddressViolations(SAMPLE_MESSAGE_ID)), NO_DEADLINE);

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactlyInAnyOrder(
                        tuple("RR03", "PmtInf[1]/CdtTrfTxInf[1]/Cdtr/Nm"),
                        tuple("RR03", "PmtInf[1]/CdtTrfTxInf[2]/Cdtr/PstlAdr"));
    }

    @Test
    void acceptsADebtorAgentIdentifiedByOtherIdentificationInsteadOfBic() {
        var xml = VALID_PAIN_001.replace("<BICFI>COBADEFFXXX</BICFI>", "<Othr><Id>NOTPROVIDED</Id></Othr>");

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.violations()).isEmpty();
    }

    @Test
    void rejectsRepeatedPaymentInformationIdWithinTheFile() {
        ValidationResult result = validator.validate(bytes(withTwoPaymentInformationBlocks("PMT-001")), NO_DEADLINE);

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("DU02", "PmtInf[2]/PmtInfId"));
    }

    @Test
    void acceptsSeveralPaymentInformationBlocksWithDistinctIds() {
        ValidationResult result = validator.validate(bytes(withTwoPaymentInformationBlocks("PMT-002")), NO_DEADLINE);

        assertThat(result.violations()).isEmpty();
    }

    private static String withTwoPaymentInformationBlocks(String secondPaymentInformationId) {
        String first = VALID_PAIN_001.substring(VALID_PAIN_001.indexOf("<PmtInf>"),
                VALID_PAIN_001.indexOf("</PmtInf>") + "</PmtInf>".length());
        String second = first.replace("<PmtInfId>PMT-001</PmtInfId>",
                "<PmtInfId>" + secondPaymentInformationId + "</PmtInfId>");
        return VALID_PAIN_001
                .replace(first, first + "\n    " + second)
                .replace("<NbOfTxs>2</NbOfTxs>\n      <CtrlSum>150.50</CtrlSum>\n      <InitgPty>",
                        "<NbOfTxs>4</NbOfTxs>\n      <CtrlSum>301.00</CtrlSum>\n      <InitgPty>");
    }

    @Test
    void rejectsSchemaInvalidFileWithFf01AndSkipsContentRules() {
        var xml = VALID_PAIN_001
                .replace("<PmtMtd>TRF</PmtMtd>", "<PmtMtd>XYZ</PmtMtd>")
                .replace("NL91ABNA0417164300", "NL91ABNA0417164301");

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations()).isNotEmpty().allSatisfy(v -> assertThat(v.reasonCode()).isEqualTo("FF01"));
        assertThat(result.originalMessageNameId()).isEqualTo("pain.001.001.09");
    }

    @Test
    void rejectsUnsupportedFormatWithFf01() {
        var xml = VALID_PAIN_001.replace("pain.001.001.09", "pain.001.001.99");

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.accepted()).isFalse();
        assertThat(result.originalMessageId()).isNull();
        assertThat(result.originalMessageNameId()).isNull();
        assertThat(result.violations()).singleElement()
                .satisfies(v -> assertThat(v.reasonCode()).isEqualTo("FF01"));
    }
}
