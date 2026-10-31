// ABOUTME: Streaming StAX parser for schema-valid pain.001.001.09 credit transfer initiation messages.
// ABOUTME: Emits payment information, transactions and computed totals to a handler; returns the group header.
package dev.thinkahead.sepa.validation.pain001;

import dev.thinkahead.sepa.validation.xml.SecureXml;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class Pain001Parser {

    private static final String ROOT = "Document/CstmrCdtTrfInitn";
    private static final String GRP_HDR = ROOT + "/GrpHdr";
    private static final String PMT_INF = ROOT + "/PmtInf";
    private static final String TX = PMT_INF + "/CdtTrfTxInf";

    public GroupHeader parse(byte[] content, Pain001Handler handler) {
        try {
            XMLStreamReader reader = SecureXml.inputFactory().createXMLStreamReader(new ByteArrayInputStream(content));
            try {
                return new Parse(reader, handler).run();
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("Unreadable pain.001 content", e);
        }
    }

    private static final class Parse {

        private final XMLStreamReader reader;
        private final Pain001Handler handler;
        private final Deque<String> elements = new ArrayDeque<>();
        private final StringBuilder text = new StringBuilder();

        private String messageId;
        private String groupNumberOfTransactions;
        private BigDecimal groupControlSum;
        private long groupCount;
        private BigDecimal groupSum = BigDecimal.ZERO;

        private int paymentInformationIndex;
        private String paymentInformationPath;
        private String paymentInformationId;
        private String paymentNumberOfTransactions;
        private BigDecimal paymentControlSum;
        private String debtorIban;
        private long paymentCount;
        private BigDecimal paymentSum;

        private int transactionIndex;
        private String endToEndId;
        private BigDecimal amount;
        private String currency;
        private String creditorIban;

        Parse(XMLStreamReader reader, Pain001Handler handler) {
            this.reader = reader;
            this.handler = handler;
        }

        GroupHeader run() throws XMLStreamException {
            while (reader.hasNext()) {
                switch (reader.next()) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        elements.addLast(reader.getLocalName());
                        text.setLength(0);
                        startElement(String.join("/", elements));
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> text.append(reader.getText());
                    case XMLStreamConstants.END_ELEMENT -> {
                        endElement(String.join("/", elements), text.toString().trim());
                        elements.removeLast();
                        text.setLength(0);
                    }
                    default -> {
                    }
                }
            }
            return new GroupHeader(messageId);
        }

        private void startElement(String path) {
            switch (path) {
                case PMT_INF -> {
                    paymentInformationIndex++;
                    paymentInformationPath = "PmtInf[" + paymentInformationIndex + "]";
                    paymentInformationId = null;
                    paymentNumberOfTransactions = null;
                    paymentControlSum = null;
                    debtorIban = null;
                    paymentCount = 0;
                    paymentSum = BigDecimal.ZERO;
                    transactionIndex = 0;
                }
                case TX -> {
                    transactionIndex++;
                    endToEndId = null;
                    amount = null;
                    currency = null;
                    creditorIban = null;
                }
                case TX + "/Amt/InstdAmt" -> currency = reader.getAttributeValue(null, "Ccy");
                default -> {
                }
            }
        }

        private void endElement(String path, String value) {
            switch (path) {
                case GRP_HDR + "/MsgId" -> messageId = value;
                case GRP_HDR + "/NbOfTxs" -> groupNumberOfTransactions = value;
                case GRP_HDR + "/CtrlSum" -> groupControlSum = new BigDecimal(value);
                case PMT_INF + "/PmtInfId" -> paymentInformationId = value;
                case PMT_INF + "/NbOfTxs" -> paymentNumberOfTransactions = value;
                case PMT_INF + "/CtrlSum" -> paymentControlSum = new BigDecimal(value);
                case PMT_INF + "/DbtrAcct/Id/IBAN" -> debtorIban = value;
                case TX + "/PmtId/EndToEndId" -> endToEndId = value;
                case TX + "/Amt/InstdAmt" -> amount = new BigDecimal(value);
                case TX + "/CdtrAcct/Id/IBAN" -> creditorIban = value;
                case TX -> endTransaction();
                case PMT_INF -> endPaymentInformation();
                case ROOT -> handler.totals(new Totals("GrpHdr", groupNumberOfTransactions, groupControlSum,
                        groupCount, groupSum));
                default -> {
                }
            }
        }

        private void endTransaction() {
            handler.transaction(new CreditTransferTransaction(
                    paymentInformationPath + "/CdtTrfTxInf[" + transactionIndex + "]",
                    endToEndId, amount, currency, creditorIban));
            paymentCount++;
            groupCount++;
            if (amount != null) {
                paymentSum = paymentSum.add(amount);
                groupSum = groupSum.add(amount);
            }
        }

        private void endPaymentInformation() {
            handler.paymentInformation(new PaymentInformation(paymentInformationPath, paymentInformationId, debtorIban));
            handler.totals(new Totals(paymentInformationPath, paymentNumberOfTransactions, paymentControlSum,
                    paymentCount, paymentSum));
        }
    }
}
