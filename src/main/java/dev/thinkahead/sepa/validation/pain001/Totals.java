// ABOUTME: Declared versus actually counted transaction totals for a group header or payment information block.
// ABOUTME: Declared values are null when the optional element is absent from the file.
package dev.thinkahead.sepa.validation.pain001;

import java.math.BigDecimal;

public record Totals(
        String path,
        String declaredNumberOfTransactions,
        BigDecimal declaredControlSum,
        long actualNumberOfTransactions,
        BigDecimal actualControlSum) {}
