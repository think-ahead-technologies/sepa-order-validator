// ABOUTME: Rule: the requested execution date of a payment information block is a TARGET business day, not in the past.
// ABOUTME: SEPA Instant is exempt: its date is the earliest start, any day (DK 2.2.4.1). Reports DT01 (InvalidDate).
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferScheme;
import dev.thinkahead.sepa.validation.pain001.PaymentInformation;
import java.time.LocalDate;
import java.util.List;

public class ExecutionDateRule implements ValidationRule<PaymentInformation> {

    private final LocalDate today;

    public ExecutionDateRule(LocalDate today) {
        this.today = today;
    }

    @Override
    public List<Violation> validate(PaymentInformation paymentInformation) {
        LocalDate date = paymentInformation.requestedExecutionDate();
        String path = paymentInformation.path() + "/ReqdExctnDt";
        if (date == null || paymentInformation.scheme() == CreditTransferScheme.SCT_INST) {
            return List.of();
        }
        if (date.isBefore(today)) {
            return List.of(new Violation("DT01", path, "Requested execution date " + date + " is in the past"));
        }
        if (!TargetCalendar.isBusinessDay(date)) {
            return List.of(new Violation("DT01", path,
                    "Requested execution date " + date + " is not a TARGET business day"));
        }
        return List.of();
    }
}
