// ABOUTME: Unit tests for the streaming pain.001.001.09 parser.
// ABOUTME: Verifies the emitted group header, payment information, transactions and computed totals.
package dev.thinkahead.sepa.validation.pain001;

import static dev.thinkahead.sepa.Fixtures.VALID_PAIN_001;
import static dev.thinkahead.sepa.Fixtures.bytes;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Pain001ParserTest {

    private final RecordingHandler handler = new RecordingHandler();

    @Test
    void returnsGroupHeader() {
        GroupHeader header = new Pain001Parser().parse(bytes(VALID_PAIN_001), handler);

        assertThat(header.messageId()).isEqualTo("MSG-2026-10-31-001");
    }

    @Test
    void emitsEachTransactionWithLocation() {
        new Pain001Parser().parse(bytes(VALID_PAIN_001), handler);

        assertThat(handler.transactions).containsExactly(
                new CreditTransferTransaction("PmtInf[1]/CdtTrfTxInf[1]", "E2E-001",
                        new BigDecimal("100.00"), "EUR", "NL91ABNA0417164300"),
                new CreditTransferTransaction("PmtInf[1]/CdtTrfTxInf[2]", "E2E-002",
                        new BigDecimal("50.50"), "EUR", "FR1420041010050500013M02606"));
    }

    @Test
    void emitsPaymentInformation() {
        new Pain001Parser().parse(bytes(VALID_PAIN_001), handler);

        assertThat(handler.paymentInformation).containsExactly(
                new PaymentInformation("PmtInf[1]", "PMT-001", "DE89370400440532013000"));
    }

    @Test
    void emitsDeclaredAndActualTotalsForPaymentInformationAndGroup() {
        new Pain001Parser().parse(bytes(VALID_PAIN_001), handler);

        assertThat(handler.totals).containsExactly(
                new Totals("PmtInf[1]", "2", new BigDecimal("150.50"), 2, new BigDecimal("150.50")),
                new Totals("GrpHdr", "2", new BigDecimal("150.50"), 2, new BigDecimal("150.50")));
    }

    @Test
    void computesActualTotalsIndependentlyOfDeclaredValues() {
        var xml = VALID_PAIN_001
                .replace("<NbOfTxs>2</NbOfTxs>", "<NbOfTxs>5</NbOfTxs>")
                .replace("<CtrlSum>150.50</CtrlSum>", "<CtrlSum>999.99</CtrlSum>");

        new Pain001Parser().parse(bytes(xml), handler);

        assertThat(handler.totals).containsExactly(
                new Totals("PmtInf[1]", "5", new BigDecimal("999.99"), 2, new BigDecimal("150.50")),
                new Totals("GrpHdr", "5", new BigDecimal("999.99"), 2, new BigDecimal("150.50")));
    }

    private static final class RecordingHandler implements Pain001Handler {
        final List<PaymentInformation> paymentInformation = new ArrayList<>();
        final List<CreditTransferTransaction> transactions = new ArrayList<>();
        final List<Totals> totals = new ArrayList<>();

        @Override
        public void paymentInformation(PaymentInformation pmtInf) {
            paymentInformation.add(pmtInf);
        }

        @Override
        public void transaction(CreditTransferTransaction transaction) {
            transactions.add(transaction);
        }

        @Override
        public void totals(Totals t) {
            totals.add(t);
        }
    }
}
