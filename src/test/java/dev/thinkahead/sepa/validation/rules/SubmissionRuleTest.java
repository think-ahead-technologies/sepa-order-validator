// ABOUTME: Unit tests for the rule matching a file's content to how it was submitted (DK order types CCT and CIP).
// ABOUTME: SEPA Instant never comes in a container, and the SEPA Instant inbox takes only SEPA Instant; both AG02.
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import dev.thinkahead.sepa.intake.Submission;
import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferScheme;
import dev.thinkahead.sepa.validation.pain001.PaymentInformation;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SubmissionRuleTest {

    private static PaymentInformation paymentInformation(CreditTransferScheme scheme) {
        return new PaymentInformation(
                "PmtInf[2]", "PMT-002", "DE89370400440532013000", null, LocalDate.of(2026, 11, 2), scheme);
    }

    @Test
    void acceptsBothSchemesSubmittedAsASingleMessageInTheRegularInbox() {
        var rule = new SubmissionRule(Submission.REGULAR);

        assertThat(rule.validate(paymentInformation(CreditTransferScheme.SCT))).isEmpty();
        assertThat(rule.validate(paymentInformation(CreditTransferScheme.SCT_INST)))
                .isEmpty();
    }

    @Test
    void acceptsSepaCreditTransfersInAContainer() {
        assertThat(new SubmissionRule(new Submission(false, true))
                        .validate(paymentInformation(CreditTransferScheme.SCT)))
                .isEmpty();
    }

    @Test
    void reportsAg02ForSepaInstantInAContainer() {
        assertThat(new SubmissionRule(new Submission(false, true))
                        .validate(paymentInformation(CreditTransferScheme.SCT_INST)))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("AG02");
                    assertThat(v.path()).isEqualTo("PmtInf[2]");
                    assertThat(v.message()).contains("container");
                });
    }

    @Test
    void acceptsSepaInstantInTheSepaInstantInbox() {
        assertThat(new SubmissionRule(new Submission(true, false))
                        .validate(paymentInformation(CreditTransferScheme.SCT_INST)))
                .isEmpty();
    }

    @Test
    void reportsAg02ForSepaCreditTransfersInTheSepaInstantInbox() {
        assertThat(new SubmissionRule(new Submission(true, false))
                        .validate(paymentInformation(CreditTransferScheme.SCT)))
                .extracting(Violation::reasonCode, Violation::path)
                .containsExactly(tuple("AG02", "PmtInf[2]"));
    }
}
