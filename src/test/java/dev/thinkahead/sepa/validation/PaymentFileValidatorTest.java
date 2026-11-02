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

import dev.thinkahead.sepa.validation.container.ContainerReading;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentFileValidatorTest {

    private static final Instant NO_DEADLINE = Instant.now().plus(Duration.ofHours(1));

    private static final Clock FRIDAY_30_OCTOBER_2026 = clockAt("2026-10-30T09:00:00Z");

    private final PaymentFileValidator validator = new PaymentFileValidator(FRIDAY_30_OCTOBER_2026);

    private static Clock clockAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void readsAContainerAsItsSplitMessages() {
        var xml = dev.thinkahead.sepa.Fixtures.container(
                dev.thinkahead.sepa.Fixtures.validPain001("MSG-A"), dev.thinkahead.sepa.Fixtures.validPain001("MSG-B"));

        Optional<ContainerReading> reading = validator.readContainer(bytes(xml), NO_DEADLINE);

        assertThat(reading)
                .get()
                .isInstanceOfSatisfying(
                        ContainerReading.Split.class,
                        split -> assertThat(split.messages()).hasSize(2));
    }

    @Test
    void readsNoContainerFromASingleMessage() {
        assertThat(validator.readContainer(bytes(VALID_PAIN_001), NO_DEADLINE)).isEmpty();
    }

    @Test
    void rejectsAContainerWhoseReadingRunsPastTheDeadlineWithFf01() {
        var xml = dev.thinkahead.sepa.Fixtures.container(dev.thinkahead.sepa.Fixtures.validPain001("MSG-A"));

        Optional<ContainerReading> reading =
                validator.readContainer(bytes(xml), Instant.now().minusSeconds(1));

        assertThat(reading)
                .get()
                .isInstanceOfSatisfying(
                        ContainerReading.Fault.class,
                        fault -> assertThat(fault.violations()).singleElement().satisfies(violation -> {
                            assertThat(violation.reasonCode()).isEqualTo("FF01");
                            assertThat(violation.message()).contains("time limit");
                        }));
    }

    @Test
    void rejectsSepaInstantFromAContainerWithAg02() {
        String xml = dev.thinkahead.sepa.Fixtures.validInstantPain001(
                SAMPLE_MESSAGE_ID, java.time.LocalDate.of(2026, 11, 2));

        ValidationResult result =
                validator.validate(bytes(xml), NO_DEADLINE, new dev.thinkahead.sepa.intake.Submission(false, true));

        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("AG02", "PmtInf[1]"));
    }

    @Test
    void rejectsSepaCreditTransfersFromTheSepaInstantInboxWithAg02() {
        ValidationResult result = validator.validate(
                bytes(VALID_PAIN_001), NO_DEADLINE, new dev.thinkahead.sepa.intake.Submission(true, false));

        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("AG02", "PmtInf[1]"));
    }

    @Test
    void acceptsSepaInstantWithAnExecutionDateInThePastOnASunday() {
        String xml = dev.thinkahead.sepa.Fixtures.validInstantPain001(
                SAMPLE_MESSAGE_ID, java.time.LocalDate.of(2026, 10, 25));

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.violations()).isEmpty();
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void rejectsAFileMixingSepaInstantAndSepaCreditTransfersWithAg02() {
        ValidationResult result = validator.validate(
                bytes(dev.thinkahead.sepa.Fixtures.withMixedSchemes(SAMPLE_MESSAGE_ID)), NO_DEADLINE);

        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("AG02", "PmtInf[2]"));
    }

    @Test
    void rejectsALocalInstrumentOtherThanInstWithAg02() {
        String xml = dev.thinkahead.sepa.Fixtures.validInstantPain001(
                        SAMPLE_MESSAGE_ID, java.time.LocalDate.of(2026, 11, 2))
                .replace("<Cd>INST</Cd>", "<Cd>SDCL</Cd>");

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("AG02", "PmtInf[1]/PmtTpInf/LclInstrm"));
    }

    @Test
    void rejectsAChequeOrderAgainstTheDkSchemaWithFf01() {
        var xml = VALID_PAIN_001.replace("<PmtMtd>TRF</PmtMtd>", "<PmtMtd>CHK</PmtMtd>");

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations())
                .isNotEmpty()
                .allSatisfy(violation -> assertThat(violation.reasonCode()).isEqualTo("FF01"));
    }

    @Test
    void rejectsAChargeBearerOtherThanSlevAgainstTheDkSchemaWithFf01() {
        var xml = VALID_PAIN_001.replace("<ChrgBr>SLEV</ChrgBr>", "<ChrgBr>DEBT</ChrgBr>");

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations())
                .isNotEmpty()
                .allSatisfy(violation -> assertThat(violation.reasonCode()).isEqualTo("FF01"));
    }

    @Test
    void reportsRuleViolationsWithTheirReasonCodesBeforeCheckingTheDkSubset() {
        var xml = VALID_PAIN_001
                .replace("<PmtMtd>TRF</PmtMtd>", "<PmtMtd>CHK</PmtMtd>")
                .replace("<CtrlSum>150.50</CtrlSum>", "<CtrlSum>150.51</CtrlSum>");

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactlyInAnyOrder(tuple("AM10", "GrpHdr/CtrlSum"), tuple("AM10", "PmtInf[1]/CtrlSum"));
    }

    @Test
    void keepsTheMessageIdOfAFileRejectedByTheDkSubset() {
        var xml = VALID_PAIN_001.replace("<ChrgBr>SLEV</ChrgBr>", "<ChrgBr>DEBT</ChrgBr>");

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.originalMessageId()).isEqualTo(SAMPLE_MESSAGE_ID);
        assertThat(result.violations())
                .first()
                .satisfies(violation -> assertThat(violation.message()).startsWith("DK subset GBIC_5: "));
    }

    @Test
    void rejectsAServiceLevelOtherThanSepaWithAg02() {
        var xml = VALID_PAIN_001.replaceFirst("(<SvcLvl>\\s*<Cd>)SEPA(</Cd>)", "$1URGP$2");

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("AG02", "PmtInf[1]/PmtTpInf/SvcLvl/Cd"));
    }

    @Test
    void rejectsPaymentTypeInformationOnBothLevelsWithFf01() {
        String xml = dev.thinkahead.sepa.Fixtures.validInstantPain001(
                        SAMPLE_MESSAGE_ID, java.time.LocalDate.of(2026, 11, 2))
                .replaceFirst(
                        "(<PmtId>\\s*<EndToEndId>E2E-001</EndToEndId>\\s*</PmtId>)",
                        "$1<PmtTpInf><LclInstrm><Cd>INST</Cd></LclInstrm></PmtTpInf>");

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("FF01", "PmtInf[1]/CdtTrfTxInf[1]/PmtTpInf"));
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

        assertThat(lateEvening.validate(bytes(VALID_PAIN_001), NO_DEADLINE).violations())
                .isEmpty();
        assertThat(afterMidnight.validate(bytes(VALID_PAIN_001), NO_DEADLINE).violations())
                .extracting(Violation::reasonCode)
                .containsExactly("DT01");
    }

    @Test
    void rejectsAFileWhoseValidationRunsPastTheDeadlineWithFf01() {
        ValidationResult result =
                validator.validate(bytes(VALID_PAIN_001), Instant.now().minusSeconds(1));

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
                .replace(
                        "<NbOfTxs>2</NbOfTxs>\n      <CtrlSum>150.50</CtrlSum>\n      <InitgPty>",
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
        ValidationResult result =
                validator.validate(bytes(withSepaFieldRuleViolations(SAMPLE_MESSAGE_ID)), NO_DEADLINE);

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
    void rejectsMissingCreditorNameAndUnstructuredAddressWithRr03() {
        ValidationResult result =
                validator.validate(bytes(withCreditorNameAndAddressViolations(SAMPLE_MESSAGE_ID)), NO_DEADLINE);

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactlyInAnyOrder(
                        tuple("RR03", "PmtInf[1]/CdtTrfTxInf[1]/Cdtr/Nm"),
                        tuple("RR03", "PmtInf[1]/CdtTrfTxInf[2]/Cdtr/PstlAdr"));
    }

    @Test
    void rejectsAPastDatedSepaInstantFileWithAnUnstructuredAddressWithRr03Only() {
        String xml = dev.thinkahead.sepa.Fixtures.pastDatedInstantWithUnstructuredCreditorAddress(
                SAMPLE_MESSAGE_ID, java.time.LocalDate.of(2026, 10, 25));

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("RR03", "PmtInf[1]/CdtTrfTxInf[2]/Cdtr/PstlAdr"));
    }

    @Test
    void rejectsAnUnstructuredAddressBefore22November2026WithRr03InsteadOfTheDkSubsetsFf01() {
        String xml = dev.thinkahead.sepa.Fixtures.withUnstructuredCreditorAddress(
                dev.thinkahead.sepa.Fixtures.validPain001(SAMPLE_MESSAGE_ID, java.time.LocalDate.of(2026, 11, 20)));

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.violations())
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("RR03", "PmtInf[1]/CdtTrfTxInf[2]/Cdtr/PstlAdr"));
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
        String first = VALID_PAIN_001.substring(
                VALID_PAIN_001.indexOf("<PmtInf>"), VALID_PAIN_001.indexOf("</PmtInf>") + "</PmtInf>".length());
        String second = first.replace(
                "<PmtInfId>PMT-001</PmtInfId>", "<PmtInfId>" + secondPaymentInformationId + "</PmtInfId>");
        return VALID_PAIN_001
                .replace(first, first + "\n    " + second)
                .replace(
                        "<NbOfTxs>2</NbOfTxs>\n      <CtrlSum>150.50</CtrlSum>\n      <InitgPty>",
                        "<NbOfTxs>4</NbOfTxs>\n      <CtrlSum>301.00</CtrlSum>\n      <InitgPty>");
    }

    @Test
    void rejectsSchemaInvalidFileWithFf01AndSkipsContentRules() {
        var xml = VALID_PAIN_001
                .replace("<PmtMtd>TRF</PmtMtd>", "<PmtMtd>XYZ</PmtMtd>")
                .replace("NL91ABNA0417164300", "NL91ABNA0417164301");

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations())
                .isNotEmpty()
                .allSatisfy(v -> assertThat(v.reasonCode()).isEqualTo("FF01"));
        assertThat(result.originalMessageNameId()).isEqualTo("pain.001.001.09");
    }

    @ParameterizedTest
    @ValueSource(strings = {"pain.001.001.03", "pain.001.003.03"})
    void rejectsPredecessorVersionsAsUnsupportedFormatWithFf01(String version) {
        // Spec format-acceptance: only pain.001.001.09 is accepted for credit transfers.
        var xml = VALID_PAIN_001.replace("pain.001.001.09", version);

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.accepted()).isFalse();
        assertThat(result.originalMessageNameId()).isNull();
        assertThat(result.violations()).singleElement().satisfies(v -> {
            assertThat(v.reasonCode()).isEqualTo("FF01");
            assertThat(v.message()).contains("Unsupported");
        });
    }

    @Test
    void rejectsUnsupportedFormatWithFf01() {
        var xml = VALID_PAIN_001.replace("pain.001.001.09", "pain.001.001.99");

        ValidationResult result = validator.validate(bytes(xml), NO_DEADLINE);

        assertThat(result.accepted()).isFalse();
        assertThat(result.originalMessageId()).isNull();
        assertThat(result.originalMessageNameId()).isNull();
        assertThat(result.violations())
                .singleElement()
                .satisfies(v -> assertThat(v.reasonCode()).isEqualTo("FF01"));
    }
}
