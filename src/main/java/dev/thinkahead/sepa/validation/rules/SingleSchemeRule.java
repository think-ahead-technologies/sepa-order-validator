// ABOUTME: Rule: one file holds either SEPA credit transfers or SEPA Instant credit transfers, never both.
// ABOUTME: Stateful per file (DK Anlage 3 v3.9, 2.1: separate order types CCT and CIP); reports AG02.
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.pain001.CreditTransferScheme;
import dev.thinkahead.sepa.validation.pain001.PaymentInformation;
import java.util.List;

public class SingleSchemeRule implements ValidationRule<PaymentInformation> {

    private static final String MESSAGE =
            "A file must not mix SEPA Instant and SEPA credit transfers, submit them in separate files";

    private CreditTransferScheme fileScheme;

    @Override
    public List<Violation> validate(PaymentInformation paymentInformation) {
        CreditTransferScheme scheme = paymentInformation.scheme();
        if (scheme == CreditTransferScheme.MIXED) {
            return List.of(new Violation("AG02", paymentInformation.path(), MESSAGE));
        }
        if (fileScheme == null) {
            fileScheme = scheme;
        }
        if (scheme == fileScheme) {
            return List.of();
        }
        return List.of(new Violation("AG02", paymentInformation.path(), MESSAGE));
    }
}
