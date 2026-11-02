// ABOUTME: Unit tests for the SEPA postal address rule of debtor and creditor (EPC C2PSP IG 2.23 and 2.118).
// ABOUTME: Addresses must contain TwnNm and Ctry at any execution date (DK TVS GBIC_5); reports RR02 or RR03.
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.Party;
import dev.thinkahead.sepa.validation.pain001.PostalAddress;
import org.junit.jupiter.api.Test;

class PostalAddressRuleTest {

    private static final String CREDITOR_ADDRESS = "PmtInf[1]/CdtTrfTxInf[2]/Cdtr/PstlAdr";
    private static final String DEBTOR_ADDRESS = "PmtInf[1]/Dbtr/PstlAdr";

    private final PostalAddressRule rule = new PostalAddressRule();

    private static PostalAddress unstructured(Party party) {
        String path = party == Party.DEBTOR ? DEBTOR_ADDRESS : CREDITOR_ADDRESS;
        return new PostalAddress(path, party, 2, false, true);
    }

    @Test
    void acceptsStructuredAndHybridAddresses() {
        assertThat(rule.validate(new PostalAddress(CREDITOR_ADDRESS, Party.CREDITOR, 0, true, true)))
                .isEmpty();
        assertThat(rule.validate(new PostalAddress(CREDITOR_ADDRESS, Party.CREDITOR, 2, true, true)))
                .isEmpty();
    }

    @Test
    void reportsRr03ForAnUnstructuredCreditorAddress() {
        assertThat(rule.validate(unstructured(Party.CREDITOR))).singleElement().satisfies(v -> {
            assertThat(v.reasonCode()).isEqualTo("RR03");
            assertThat(v.path()).isEqualTo(CREDITOR_ADDRESS);
            assertThat(v.message()).contains("TwnNm").contains("Ctry");
        });
    }

    @Test
    void reportsRr03ForAnUnstructuredCreditorAddressWithoutCountry() {
        var address = new PostalAddress(CREDITOR_ADDRESS, Party.CREDITOR, 1, false, false);

        assertThat(rule.validate(address)).extracting(Violation::reasonCode).containsExactly("RR03");
    }

    @Test
    void reportsRr02ForAnUnstructuredDebtorAddress() {
        assertThat(rule.validate(unstructured(Party.DEBTOR)))
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("RR02", DEBTOR_ADDRESS));
    }

    @Test
    void reportsATownWithoutCountry() {
        var address = new PostalAddress(CREDITOR_ADDRESS, Party.CREDITOR, 0, true, false);

        assertThat(rule.validate(address)).extracting(Violation::reasonCode).containsExactly("RR03");
    }

    @Test
    void reportsAStructuredAddressWithoutTownName() {
        var address = new PostalAddress(CREDITOR_ADDRESS, Party.CREDITOR, 0, false, true);

        assertThat(rule.validate(address)).extracting(Violation::reasonCode).containsExactly("RR03");
    }

    @Test
    void reportsMoreThanTwoAddressLines() {
        var address = new PostalAddress(DEBTOR_ADDRESS, Party.DEBTOR, 3, true, true);

        assertThat(rule.validate(address)).singleElement().satisfies(v -> {
            assertThat(v.reasonCode()).isEqualTo("RR02");
            assertThat(v.path()).isEqualTo(DEBTOR_ADDRESS + "/AdrLine");
            assertThat(v.message()).contains("3");
        });
    }
}
