// ABOUTME: Unit tests for the SEPA postal address rule of debtor and creditor (EPC C2PSP IG 2.23 and 2.118).
// ABOUTME: Unstructured addresses are allowed only for execution before 22 November 2026; reports RR02 or RR03.
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.Party;
import dev.thinkahead.sepa.validation.pain001.PostalAddress;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PostalAddressRuleTest {

    private static final String CREDITOR_ADDRESS = "PmtInf[1]/CdtTrfTxInf[2]/Cdtr/PstlAdr";
    private static final String DEBTOR_ADDRESS = "PmtInf[1]/Dbtr/PstlAdr";
    private static final LocalDate LAST_DAY_BEFORE_CUTOVER = LocalDate.of(2026, 11, 21);
    private static final LocalDate CUTOVER = LocalDate.of(2026, 11, 22);

    private final PostalAddressRule rule = new PostalAddressRule();

    private static PostalAddress structured(LocalDate executionDate) {
        return new PostalAddress(CREDITOR_ADDRESS, Party.CREDITOR, executionDate, 0, true, true, true);
    }

    private static PostalAddress hybrid(LocalDate executionDate) {
        return new PostalAddress(CREDITOR_ADDRESS, Party.CREDITOR, executionDate, 2, true, true, true);
    }

    private static PostalAddress unstructured(Party party, LocalDate executionDate) {
        String path = party == Party.DEBTOR ? DEBTOR_ADDRESS : CREDITOR_ADDRESS;
        return new PostalAddress(path, party, executionDate, 2, false, true, false);
    }

    @Test
    void acceptsStructuredAndHybridAddressesBeforeAndAfterTheCutover() {
        assertThat(rule.validate(structured(LAST_DAY_BEFORE_CUTOVER))).isEmpty();
        assertThat(rule.validate(structured(CUTOVER))).isEmpty();
        assertThat(rule.validate(hybrid(LAST_DAY_BEFORE_CUTOVER))).isEmpty();
        assertThat(rule.validate(hybrid(CUTOVER))).isEmpty();
    }

    @Test
    void acceptsAnUnstructuredAddressForExecutionBeforeTheCutover() {
        assertThat(rule.validate(unstructured(Party.CREDITOR, LAST_DAY_BEFORE_CUTOVER))).isEmpty();
    }

    @Test
    void acceptsAnUnstructuredAddressWithoutCountryBeforeTheCutover() {
        var address = new PostalAddress(CREDITOR_ADDRESS, Party.CREDITOR, LAST_DAY_BEFORE_CUTOVER, 1, false, false,
                false);

        assertThat(rule.validate(address)).isEmpty();
    }

    @Test
    void reportsRr03ForAnUnstructuredCreditorAddressFromTheCutoverOn() {
        assertThat(rule.validate(unstructured(Party.CREDITOR, CUTOVER)))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("RR03");
                    assertThat(v.path()).isEqualTo(CREDITOR_ADDRESS);
                    assertThat(v.message()).contains("TwnNm").contains("Ctry").contains("2026-11-22");
                });
    }

    @Test
    void reportsRr02ForAnUnstructuredDebtorAddressFromTheCutoverOn() {
        assertThat(rule.validate(unstructured(Party.DEBTOR, CUTOVER.plusMonths(3))))
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("RR02", DEBTOR_ADDRESS));
    }

    @Test
    void reportsAStructuredAddressWithoutTownNameFromTheCutoverOn() {
        var address = new PostalAddress(CREDITOR_ADDRESS, Party.CREDITOR, CUTOVER, 0, false, true, true);

        assertThat(rule.validate(address)).extracting(Violation::reasonCode).containsExactly("RR03");
    }

    @Test
    void reportsAMixedAddressWithoutTownNameOrCountryBeforeTheCutover() {
        // Neither structured/hybrid (TwnNm and Ctry missing) nor unstructured (StrtNm present).
        var address = new PostalAddress(CREDITOR_ADDRESS, Party.CREDITOR, LAST_DAY_BEFORE_CUTOVER, 1, false, false,
                true);

        assertThat(rule.validate(address))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("RR03");
                    assertThat(v.path()).isEqualTo(CREDITOR_ADDRESS);
                });
    }

    @Test
    void reportsAnAddressWithoutAnyLinesOrTownBeforeTheCutover() {
        var address = new PostalAddress(CREDITOR_ADDRESS, Party.CREDITOR, LAST_DAY_BEFORE_CUTOVER, 0, false, true,
                false);

        assertThat(rule.validate(address)).extracting(Violation::reasonCode).containsExactly("RR03");
    }

    @Test
    void reportsMoreThanTwoAddressLines() {
        var address = new PostalAddress(DEBTOR_ADDRESS, Party.DEBTOR, LAST_DAY_BEFORE_CUTOVER, 3, true, true, true);

        assertThat(rule.validate(address))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("RR02");
                    assertThat(v.path()).isEqualTo(DEBTOR_ADDRESS + "/AdrLine");
                    assertThat(v.message()).contains("3");
                });
    }
}
