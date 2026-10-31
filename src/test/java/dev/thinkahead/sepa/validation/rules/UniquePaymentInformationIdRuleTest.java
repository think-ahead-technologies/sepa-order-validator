// ABOUTME: Unit tests for the rule that every PmtInfId must be unique within one payment file.
// ABOUTME: The rule is stateful per file and reports DU02 at each repeated occurrence.
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import dev.thinkahead.sepa.validation.pain001.CreditTransferScheme;
import dev.thinkahead.sepa.validation.pain001.PaymentInformation;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class UniquePaymentInformationIdRuleTest {

    private static final String IBAN = "DE89370400440532013000";
    private static final LocalDate EXECUTION_DATE = LocalDate.of(2026, 11, 2);

    private final UniquePaymentInformationIdRule rule = new UniquePaymentInformationIdRule();

    private static PaymentInformation paymentInformation(int index, String paymentInformationId) {
        return new PaymentInformation("PmtInf[" + index + "]", paymentInformationId, IBAN, null, EXECUTION_DATE,
                CreditTransferScheme.SCT);
    }

    @Test
    void acceptsDistinctPaymentInformationIds() {
        assertThat(rule.validate(paymentInformation(1, "PMT-001"))).isEmpty();
        assertThat(rule.validate(paymentInformation(2, "PMT-002"))).isEmpty();
    }

    @Test
    void reportsDu02AtTheRepeatedOccurrence() {
        rule.validate(paymentInformation(1, "PMT-001"));
        rule.validate(paymentInformation(2, "PMT-002"));

        assertThat(rule.validate(paymentInformation(3, "PMT-001")))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("DU02");
                    assertThat(v.path()).isEqualTo("PmtInf[3]/PmtInfId");
                    assertThat(v.message()).contains("PMT-001");
                });
    }
}
