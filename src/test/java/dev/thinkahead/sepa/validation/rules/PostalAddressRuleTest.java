// ABOUTME: Unit tests for the SEPA postal address rule of debtor and creditor (EPC C2PSP IG 2.23 and 2.118).
// ABOUTME: Addresses must contain TwnNm and Ctry at any execution date (DK TVS GBIC_5); reports RR02 or RR03.
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
    private static final LocalDate BEFORE_22_NOVEMBER_2026 = LocalDate.of(2026, 11, 21);
    private static final LocalDate FROM_22_NOVEMBER_2026 = LocalDate.of(2026, 11, 22);

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
    void acceptsStructuredAndHybridAddressesAtAnyExecutionDate() {
        assertThat(rule.validate(structured(BEFORE_22_NOVEMBER_2026))).isEmpty();
        assertThat(rule.validate(structured(FROM_22_NOVEMBER_2026))).isEmpty();
        assertThat(rule.validate(hybrid(BEFORE_22_NOVEMBER_2026))).isEmpty();
        assertThat(rule.validate(hybrid(FROM_22_NOVEMBER_2026))).isEmpty();
    }

    @Test
    void reportsRr03ForAnUnstructuredCreditorAddressBefore22November2026() {
        assertThat(rule.validate(unstructured(Party.CREDITOR, BEFORE_22_NOVEMBER_2026)))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("RR03");
                    assertThat(v.path()).isEqualTo(CREDITOR_ADDRESS);
                    assertThat(v.message()).contains("TwnNm").contains("Ctry");
                });
    }

    @Test
    void reportsRr03ForAnUnstructuredCreditorAddressFrom22November2026() {
        assertThat(rule.validate(unstructured(Party.CREDITOR, FROM_22_NOVEMBER_2026)))
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("RR03", CREDITOR_ADDRESS));
    }

    @Test
    void reportsRr03ForAnUnstructuredCreditorAddressWithoutCountry() {
        var address = new PostalAddress(CREDITOR_ADDRESS, Party.CREDITOR, BEFORE_22_NOVEMBER_2026, 1, false, false,
                false);

        assertThat(rule.validate(address)).extracting(Violation::reasonCode).containsExactly("RR03");
    }

    @Test
    void reportsRr02ForAnUnstructuredDebtorAddress() {
        assertThat(rule.validate(unstructured(Party.DEBTOR, BEFORE_22_NOVEMBER_2026)))
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("RR02", DEBTOR_ADDRESS));
    }

    @Test
    void reportsATownWithoutCountry() {
        var address = new PostalAddress(CREDITOR_ADDRESS, Party.CREDITOR, BEFORE_22_NOVEMBER_2026, 0, true, false,
                true);

        assertThat(rule.validate(address)).extracting(Violation::reasonCode).containsExactly("RR03");
    }

    @Test
    void reportsAStructuredAddressWithoutTownName() {
        var address = new PostalAddress(CREDITOR_ADDRESS, Party.CREDITOR, FROM_22_NOVEMBER_2026, 0, false, true, true);

        assertThat(rule.validate(address)).extracting(Violation::reasonCode).containsExactly("RR03");
    }

    @Test
    void reportsMoreThanTwoAddressLines() {
        var address = new PostalAddress(DEBTOR_ADDRESS, Party.DEBTOR, BEFORE_22_NOVEMBER_2026, 3, true, true, true);

        assertThat(rule.validate(address))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("RR02");
                    assertThat(v.path()).isEqualTo(DEBTOR_ADDRESS + "/AdrLine");
                    assertThat(v.message()).contains("3");
                });
    }
}
