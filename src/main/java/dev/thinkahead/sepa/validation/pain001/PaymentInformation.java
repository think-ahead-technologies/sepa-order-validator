// ABOUTME: Debtor-side data of one payment information block (PmtInf) in a pain.001 message.
// ABOUTME: The path locates the block in the file, e.g. "PmtInf[1]".
package dev.thinkahead.sepa.validation.pain001;

import java.time.LocalDate;

public record PaymentInformation(
        String path,
        String paymentInformationId,
        String debtorIban,
        Agent debtorAgent,
        LocalDate requestedExecutionDate,
        CreditTransferScheme scheme) {
}
