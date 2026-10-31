// ABOUTME: Unit tests for the SEPA Instant rules on payment type information (DK Anlage 3 v3.7, 2.2.4.1).
// ABOUTME: Local instrument only INST (AG02), no mixing of SCT and SCT Inst (AG02), PmtTpInf on one level only (FF01).
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferScheme;
import dev.thinkahead.sepa.validation.pain001.PaymentInformation;
import dev.thinkahead.sepa.validation.pain001.PaymentTypeInformation;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PaymentTypeRulesTest {

    private static PaymentInformation paymentInformation(int index, CreditTransferScheme scheme) {
        return new PaymentInformation("PmtInf[" + index + "]", "PMT-00" + index, "DE89370400440532013000", null,
                LocalDate.of(2026, 11, 2), scheme);
    }

    @Test
    void localInstrumentRuleAcceptsNoLocalInstrumentAndInst() {
        var rule = new LocalInstrumentRule();

        assertThat(rule.validate(new PaymentTypeInformation("PmtInf[1]/PmtTpInf", null, null))).isEmpty();
        assertThat(rule.validate(new PaymentTypeInformation("PmtInf[1]/PmtTpInf", "INST", null))).isEmpty();
    }

    @Test
    void localInstrumentRuleReportsAg02ForOtherCodes() {
        assertThat(new LocalInstrumentRule().validate(
                new PaymentTypeInformation("PmtInf[1]/CdtTrfTxInf[3]/PmtTpInf", "SDCL", null)))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("AG02");
                    assertThat(v.path()).isEqualTo("PmtInf[1]/CdtTrfTxInf[3]/PmtTpInf/LclInstrm");
                    assertThat(v.message()).contains("SDCL").contains("INST");
                });
    }

    @Test
    void localInstrumentRuleReportsAg02ForAProprietaryLocalInstrument() {
        assertThat(new LocalInstrumentRule().validate(
                new PaymentTypeInformation("PmtInf[1]/PmtTpInf", null, "INSTANT")))
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("AG02", "PmtInf[1]/PmtTpInf/LclInstrm"));
    }

    @Test
    void levelRuleAcceptsPaymentTypeInformationOnEitherLevel() {
        var rule = new PaymentTypeInformationLevelRule();

        assertThat(rule.validate(new PaymentTypeInformation("PmtInf[1]/PmtTpInf", "INST", null))).isEmpty();
        assertThat(rule.validate(new PaymentTypeInformation("PmtInf[2]/CdtTrfTxInf[1]/PmtTpInf", "INST", null)))
                .isEmpty();
    }

    @Test
    void levelRuleReportsFf01WhenBothLevelsCarryPaymentTypeInformation() {
        var rule = new PaymentTypeInformationLevelRule();
        rule.validate(new PaymentTypeInformation("PmtInf[1]/PmtTpInf", "INST", null));

        assertThat(rule.validate(new PaymentTypeInformation("PmtInf[1]/CdtTrfTxInf[2]/PmtTpInf", "INST", null)))
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("FF01", "PmtInf[1]/CdtTrfTxInf[2]/PmtTpInf"));
    }

    @Test
    void schemeRuleAcceptsFilesOfOneScheme() {
        var sct = new SingleSchemeRule();
        var instant = new SingleSchemeRule();

        assertThat(sct.validate(paymentInformation(1, CreditTransferScheme.SCT))).isEmpty();
        assertThat(sct.validate(paymentInformation(2, CreditTransferScheme.SCT))).isEmpty();
        assertThat(instant.validate(paymentInformation(1, CreditTransferScheme.SCT_INST))).isEmpty();
        assertThat(instant.validate(paymentInformation(2, CreditTransferScheme.SCT_INST))).isEmpty();
    }

    @Test
    void schemeRuleReportsAg02ForAPaymentInformationBlockOfAnotherScheme() {
        var rule = new SingleSchemeRule();
        rule.validate(paymentInformation(1, CreditTransferScheme.SCT_INST));

        assertThat(rule.validate(paymentInformation(2, CreditTransferScheme.SCT)))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("AG02");
                    assertThat(v.path()).isEqualTo("PmtInf[2]");
                    assertThat(v.message()).contains("SEPA Instant").contains("mix");
                });
    }

    @Test
    void schemeRuleReportsAg02ForMixedTransactionsWithinOneBlock() {
        assertThat(new SingleSchemeRule().validate(paymentInformation(1, CreditTransferScheme.MIXED)))
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("AG02", "PmtInf[1]"));
    }
}
