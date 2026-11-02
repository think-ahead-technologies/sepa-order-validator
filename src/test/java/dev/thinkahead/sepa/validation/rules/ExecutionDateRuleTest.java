// ABOUTME: Unit tests for the rule that a requested execution date is a TARGET business day and not in the past.
// ABOUTME: Today is passed in by the caller, so the tests pin it; violations carry DT01 (InvalidDate).
package dev.thinkahead.sepa.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import dev.thinkahead.sepa.validation.pain001.CreditTransferScheme;
import dev.thinkahead.sepa.validation.pain001.PaymentInformation;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ExecutionDateRuleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    private final ExecutionDateRule rule = new ExecutionDateRule(TODAY);

    private static PaymentInformation paymentInformation(LocalDate requestedExecutionDate) {
        return paymentInformation(requestedExecutionDate, CreditTransferScheme.SCT);
    }

    private static PaymentInformation paymentInformation(
            LocalDate requestedExecutionDate, CreditTransferScheme scheme) {
        return new PaymentInformation(
                "PmtInf[2]", "PMT-002", "DE89370400440532013000", null, requestedExecutionDate, scheme);
    }

    @Test
    void acceptsAnyDateForSepaInstantAsEarliestStartOfExecution() {
        // DK Anlage 3 v3.9, 2.2.4.1: a date not in the future means "immediately"; SCT Inst runs 24/7/365.
        assertThat(rule.validate(paymentInformation(LocalDate.of(2026, 10, 1), CreditTransferScheme.SCT_INST)))
                .isEmpty();
        assertThat(rule.validate(paymentInformation(LocalDate.of(2026, 11, 7), CreditTransferScheme.SCT_INST)))
                .isEmpty();
        assertThat(rule.validate(paymentInformation(LocalDate.of(2026, 12, 25), CreditTransferScheme.SCT_INST)))
                .isEmpty();
    }

    @Test
    void acceptsTodayAndFutureBusinessDays() {
        assertThat(rule.validate(paymentInformation(TODAY))).isEmpty();
        assertThat(rule.validate(paymentInformation(LocalDate.of(2026, 11, 2)))).isEmpty();
    }

    @Test
    void reportsDt01ForADateInThePast() {
        assertThat(rule.validate(paymentInformation(LocalDate.of(2026, 10, 29))))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("DT01");
                    assertThat(v.path()).isEqualTo("PmtInf[2]/ReqdExctnDt");
                    assertThat(v.message()).contains("2026-10-29").contains("past");
                });
    }

    @Test
    void reportsDt01ForAWeekendDay() {
        assertThat(rule.validate(paymentInformation(LocalDate.of(2026, 11, 7))))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.reasonCode()).isEqualTo("DT01");
                    assertThat(v.path()).isEqualTo("PmtInf[2]/ReqdExctnDt");
                    assertThat(v.message()).contains("2026-11-07").contains("TARGET business day");
                });
    }

    @Test
    void reportsDt01ForATargetClosingDay() {
        assertThat(rule.validate(paymentInformation(LocalDate.of(2026, 12, 25))))
                .singleElement()
                .satisfies(v -> assertThat(v.reasonCode()).isEqualTo("DT01"));
    }

    @Test
    void reportsAPastWeekendDayOnlyOnceAsPast() {
        assertThat(rule.validate(paymentInformation(LocalDate.of(2026, 10, 25))))
                .singleElement()
                .satisfies(v -> assertThat(v.message()).contains("past"));
    }
}
