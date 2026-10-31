// ABOUTME: Rule: payment type information is given either per payment information block or per transaction, not both.
// ABOUTME: Stateful per file (DK Anlage 3 v3.9, 2.2.4.1); reports FF01 at the transaction-level PmtTpInf.
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.PaymentTypeInformation;
import java.util.List;

public class PaymentTypeInformationLevelRule implements ValidationRule<PaymentTypeInformation> {

    private static final String TRANSACTION_LEVEL = "/CdtTrfTxInf[";

    // Path prefix of the last payment information block with its own PmtTpInf, e.g. "PmtInf[1]/".
    private String paymentInformationWithPaymentType;

    @Override
    public List<Violation> validate(PaymentTypeInformation paymentType) {
        String path = paymentType.path();
        if (!path.contains(TRANSACTION_LEVEL)) {
            paymentInformationWithPaymentType = path.substring(0, path.indexOf('/') + 1);
            return List.of();
        }
        if (paymentInformationWithPaymentType == null || !path.startsWith(paymentInformationWithPaymentType)) {
            return List.of();
        }
        return List.of(new Violation("FF01", path,
                "Payment type information must not be given on payment information and transaction level"));
    }
}
