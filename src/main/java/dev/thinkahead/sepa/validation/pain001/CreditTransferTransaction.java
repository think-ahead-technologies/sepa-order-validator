// ABOUTME: One credit transfer transaction (CdtTrfTxInf) of a pain.001 message.
// ABOUTME: The path locates the transaction in the file, e.g. "PmtInf[1]/CdtTrfTxInf[2]"; agent and name may be null.
package dev.thinkahead.sepa.validation.pain001;

import java.math.BigDecimal;

public record CreditTransferTransaction(
        String path,
        String endToEndId,
        BigDecimal amount,
        String currency,
        String creditorIban,
        Agent creditorAgent,
        RemittanceInformation remittanceInformation,
        String creditorName) {}
