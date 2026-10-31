// ABOUTME: Unit tests for the SEPA remittance information and creditor reference rules (EPC C2PSP IG 2.164-2.175).
// ABOUTME: Either one unstructured or one structured remittance of at most 140 characters; SCOR creditor references.
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferTransaction;
import dev.thinkahead.sepa.validation.pain001.CreditorReference;
import dev.thinkahead.sepa.validation.pain001.RemittanceInformation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RemittanceInformationRulesTest {

    private static final String TX = "PmtInf[1]/CdtTrfTxInf[1]";

    private final RemittanceInformationRule remittanceRule = new RemittanceInformationRule();
    private final CreditorReferenceRule creditorReferenceRule = new CreditorReferenceRule();

    private static CreditTransferTransaction transaction(RemittanceInformation remittanceInformation) {
        return new CreditTransferTransaction(TX, "E2E-001", new BigDecimal("100.00"), "EUR", "NL91ABNA0417164300",
                null, remittanceInformation, "Lieferant AG");
    }

    private static RemittanceInformation structured(CreditorReference creditorReference) {
        return new RemittanceInformation(0, 1, 113, creditorReference);
    }

    @Test
    void acceptsNoRemittanceInformation() {
        assertThat(remittanceRule.validate(transaction(RemittanceInformation.NONE))).isEmpty();
    }

    @Test
    void acceptsOneUnstructuredRemittanceInformation() {
        assertThat(remittanceRule.validate(transaction(new RemittanceInformation(1, 0, 0, null)))).isEmpty();
    }

    @Test
    void acceptsOneStructuredRemittanceInformationOfExactly140Characters() {
        assertThat(remittanceRule.validate(transaction(new RemittanceInformation(0, 1, 140, null)))).isEmpty();
    }

    @Test
    void reportsFf01WhenUnstructuredAndStructuredAreBothPresent() {
        assertThat(remittanceRule.validate(transaction(new RemittanceInformation(1, 1, 50, null))))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("FF01");
                    assertThat(v.path()).isEqualTo(TX + "/RmtInf");
                    assertThat(v.message()).contains("not both");
                });
    }

    @Test
    void reportsFf01ForMoreThanOneOccurrenceOfEachKind() {
        assertThat(remittanceRule.validate(transaction(new RemittanceInformation(2, 0, 0, null))))
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("FF01", TX + "/RmtInf/Ustrd"));
        assertThat(remittanceRule.validate(transaction(new RemittanceInformation(0, 3, 40, null))))
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("FF01", TX + "/RmtInf/Strd"));
    }

    @Test
    void reportsFf01ForStructuredRemittanceInformationLongerThan140Characters() {
        assertThat(remittanceRule.validate(transaction(new RemittanceInformation(0, 1, 141, null))))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("FF01");
                    assertThat(v.path()).isEqualTo(TX + "/RmtInf/Strd");
                    assertThat(v.message()).contains("141").contains("140");
                });
    }

    @Test
    void acceptsAnRfCreditorReferenceWithScorType() {
        var reference = new CreditorReference(true, "SCOR", "RF18539007547034");

        assertThat(creditorReferenceRule.validate(transaction(structured(reference)))).isEmpty();
    }

    @Test
    void doesNotValidateCheckDigitsOfTheCreditorReference() {
        // EPC C2PSP IG 2.175: the receiving PSP is not required to validate a check digit in the reference.
        var reference = new CreditorReference(true, "SCOR", "RF19539007547034");

        assertThat(creditorReferenceRule.validate(transaction(structured(reference)))).isEmpty();
    }

    @Test
    void acceptsAProprietaryCreditorReferenceType() {
        var reference = new CreditorReference(true, null, "INV-2026-0815");

        assertThat(creditorReferenceRule.validate(transaction(structured(reference)))).isEmpty();
    }

    @Test
    void acceptsStructuredRemittanceInformationWithoutCreditorReference() {
        assertThat(creditorReferenceRule.validate(transaction(structured(null)))).isEmpty();
    }

    @Test
    void reportsFf01WhenTheCreditorReferenceTypeIsMissing() {
        var reference = new CreditorReference(false, null, "RF18539007547034");

        assertThat(creditorReferenceRule.validate(transaction(structured(reference))))
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("FF01", TX + "/RmtInf/Strd/CdtrRefInf/Tp"));
    }

    @Test
    void reportsFf01ForATypeCodeOtherThanScor() {
        var reference = new CreditorReference(true, "CINV", "RF18539007547034");

        assertThat(creditorReferenceRule.validate(transaction(structured(reference))))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("FF01");
                    assertThat(v.path()).isEqualTo(TX + "/RmtInf/Strd/CdtrRefInf/Tp/CdOrPrtry/Cd");
                    assertThat(v.message()).contains("CINV").contains("SCOR");
                });
    }

    @Test
    void reportsFf01WhenTheReferenceIsMissing() {
        var reference = new CreditorReference(true, "SCOR", null);

        assertThat(creditorReferenceRule.validate(transaction(structured(reference))))
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("FF01", TX + "/RmtInf/Strd/CdtrRefInf/Ref"));
    }
}
