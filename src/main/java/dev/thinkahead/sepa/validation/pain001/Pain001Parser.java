// ABOUTME: Streaming StAX parser for schema-valid pain.001.001.09 credit transfer initiation messages.
// ABOUTME: Emits payment information, transactions, parties, element texts and totals; returns the group header.
package dev.thinkahead.sepa.validation.pain001;

import dev.thinkahead.sepa.validation.xml.SecureXml;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    private static final String DEBTOR_AGENT = PMT_INF + "/DbtrAgt";
    private static final String CREDITOR_AGENT = TX + "/CdtrAgt";
    private static final String STRD = TX + "/RmtInf/Strd";
    private static final String CDTR_REF_INF = STRD + "/CdtrRefInf";
    private static final String DEBTOR_ADDRESS = PMT_INF + "/Dbtr/PstlAdr";
    private static final String CREDITOR_ADDRESS = TX + "/Cdtr/PstlAdr";

    public GroupHeader parse(byte[] content, Pain001Handler handler) {
        return parse(new ByteArrayInputStream(content), handler);
    }

    public GroupHeader parse(InputStream content, Pain001Handler handler) {
        try {
            XMLStreamReader reader = SecureXml.inputFactory().createXMLStreamReader(content);
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
        private LocalDate requestedExecutionDate;
        private String debtorIban;
        private AgentBuilder debtorAgent;
        private long paymentCount;
        private BigDecimal paymentSum;

        private int transactionIndex;
        private String transactionPath;
        private String endToEndId;
        private BigDecimal amount;
        private String currency;
        private String creditorIban;
        private AgentBuilder creditorAgent;
        private String creditorName;
        private AddressBuilder address;
        private int unstructuredCount;
        private int structuredCount;
        private int structuredLength;
        private int longestStructuredLength;
        private boolean creditorReferencePresent;
        private boolean creditorReferenceTypePresent;
        private String creditorReferenceTypeCode;
        private String creditorReference;

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
            if (path.startsWith(STRD + "/")) {
                structuredLength += startTagLength();
            }
            if (address != null && (path.startsWith(DEBTOR_ADDRESS + "/") || path.startsWith(CREDITOR_ADDRESS + "/"))) {
                address.element(reader.getLocalName());
            }
            if (debtorAgent != null && path.startsWith(DEBTOR_AGENT + "/")) {
                debtorAgent.element(path.substring(DEBTOR_AGENT.length() + 1));
            }
            if (creditorAgent != null && path.startsWith(CREDITOR_AGENT + "/")) {
                creditorAgent.element(path.substring(CREDITOR_AGENT.length() + 1));
            }
            switch (path) {
                case PMT_INF -> {
                    paymentInformationIndex++;
                    paymentInformationPath = "PmtInf[" + paymentInformationIndex + "]";
                    paymentInformationId = null;
                    paymentNumberOfTransactions = null;
                    paymentControlSum = null;
                    debtorIban = null;
                    debtorAgent = null;
                    requestedExecutionDate = null;
                    paymentCount = 0;
                    paymentSum = BigDecimal.ZERO;
                    transactionIndex = 0;
                }
                case TX -> {
                    transactionIndex++;
                    transactionPath = paymentInformationPath + "/CdtTrfTxInf[" + transactionIndex + "]";
                    endToEndId = null;
                    amount = null;
                    currency = null;
                    creditorIban = null;
                    creditorAgent = null;
                    creditorName = null;
                    unstructuredCount = 0;
                    structuredCount = 0;
                    longestStructuredLength = 0;
                    creditorReferencePresent = false;
                    creditorReferenceTypePresent = false;
                    creditorReferenceTypeCode = null;
                    creditorReference = null;
                }
                case TX + "/Amt/InstdAmt" -> currency = reader.getAttributeValue(null, "Ccy");
                case DEBTOR_AGENT -> debtorAgent = new AgentBuilder();
                case CREDITOR_AGENT -> creditorAgent = new AgentBuilder();
                case DEBTOR_ADDRESS, CREDITOR_ADDRESS -> address = new AddressBuilder();
                case STRD -> {
                    structuredCount++;
                    structuredLength = 0;
                }
                case CDTR_REF_INF -> creditorReferencePresent = true;
                case CDTR_REF_INF + "/Tp" -> creditorReferenceTypePresent = true;
                default -> {
                }
            }
        }

        private void endElement(String path, String value) {
            if (!value.isEmpty()) {
                handler.elementText(new ElementText(locate(path), value));
            }
            if (path.startsWith(STRD + "/")) {
                structuredLength += value.length() + reader.getLocalName().length() + "</>".length();
            }
            switch (path) {
                case GRP_HDR + "/MsgId" -> messageId = value;
                case GRP_HDR + "/NbOfTxs" -> groupNumberOfTransactions = value;
                case GRP_HDR + "/CtrlSum" -> groupControlSum = new BigDecimal(value);
                case GRP_HDR + "/InitgPty/Nm" -> handler.partyName(new PartyName("GrpHdr/InitgPty/Nm", value));
                case PMT_INF + "/PmtInfId" -> paymentInformationId = value;
                case PMT_INF + "/NbOfTxs" -> paymentNumberOfTransactions = value;
                case PMT_INF + "/CtrlSum" -> paymentControlSum = new BigDecimal(value);
                case PMT_INF + "/ReqdExctnDt/Dt" -> requestedExecutionDate = LocalDate.parse(value);
                case PMT_INF + "/ReqdExctnDt/DtTm" -> requestedExecutionDate = LocalDate.parse(value.substring(0, 10));
                case DEBTOR_ADDRESS -> handler.postalAddress(
                        address.build(paymentInformationPath + "/Dbtr/PstlAdr", Party.DEBTOR, requestedExecutionDate));
                case CREDITOR_ADDRESS -> handler.postalAddress(
                        address.build(transactionPath + "/Cdtr/PstlAdr", Party.CREDITOR, requestedExecutionDate));
                case PMT_INF + "/Dbtr/Nm" -> handler.partyName(new PartyName(paymentInformationPath + "/Dbtr/Nm", value));
                case PMT_INF + "/UltmtDbtr/Nm" ->
                        handler.partyName(new PartyName(paymentInformationPath + "/UltmtDbtr/Nm", value));
                case PMT_INF + "/DbtrAcct/Id/IBAN" -> debtorIban = value;
                case DEBTOR_AGENT + "/FinInstnId/BICFI" -> debtorAgent.bic = value;
                case DEBTOR_AGENT + "/FinInstnId/Othr/Id" -> debtorAgent.otherIdentification = value;
                case TX + "/PmtId/EndToEndId" -> endToEndId = value;
                case TX + "/Amt/InstdAmt" -> amount = new BigDecimal(value);
                case TX + "/UltmtDbtr/Nm" -> handler.partyName(new PartyName(transactionPath + "/UltmtDbtr/Nm", value));
                case CREDITOR_AGENT + "/FinInstnId/BICFI" -> creditorAgent.bic = value;
                case TX + "/Cdtr/Nm" -> {
                    creditorName = value;
                    handler.partyName(new PartyName(transactionPath + "/Cdtr/Nm", value));
                }
                case TX + "/CdtrAcct/Id/IBAN" -> creditorIban = value;
                case TX + "/UltmtCdtr/Nm" -> handler.partyName(new PartyName(transactionPath + "/UltmtCdtr/Nm", value));
                case TX + "/RmtInf/Ustrd" -> unstructuredCount++;
                case STRD -> longestStructuredLength = Math.max(longestStructuredLength, structuredLength);
                case CDTR_REF_INF + "/Tp/CdOrPrtry/Cd" -> creditorReferenceTypeCode = value;
                case CDTR_REF_INF + "/Ref" -> creditorReference = value;
                case TX -> endTransaction();
                case PMT_INF -> endPaymentInformation();
                case ROOT -> handler.totals(new Totals("GrpHdr", groupNumberOfTransactions, groupControlSum,
                        groupCount, groupSum));
                default -> {
                }
            }
        }

        /**
         * The element's location with the indices of its payment information block and transaction.
         */
        private String locate(String path) {
            if (path.startsWith(TX + "/")) {
                return transactionPath + path.substring(TX.length());
            }
            if (path.startsWith(PMT_INF + "/")) {
                return paymentInformationPath + path.substring(PMT_INF.length());
            }
            if (path.startsWith(ROOT + "/")) {
                return path.substring(ROOT.length() + 1);
            }
            return path;
        }

        /**
         * Length of the current start tag as written without namespace prefix, e.g. {@code <DuePyblAmt Ccy="EUR">}.
         */
        private int startTagLength() {
            int length = reader.getLocalName().length() + "<>".length();
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                length += reader.getAttributeLocalName(i).length() + reader.getAttributeValue(i).length()
                        + " =\"\"".length();
            }
            return length;
        }

        private void endTransaction() {
            CreditorReference reference = creditorReferencePresent
                    ? new CreditorReference(creditorReferenceTypePresent, creditorReferenceTypeCode, creditorReference)
                    : null;
            handler.transaction(new CreditTransferTransaction(transactionPath, endToEndId, amount, currency,
                    creditorIban, creditorAgent == null ? null : creditorAgent.build(),
                    new RemittanceInformation(unstructuredCount, structuredCount, longestStructuredLength,
                            reference),
                    creditorName));
            paymentCount++;
            groupCount++;
            if (amount != null) {
                paymentSum = paymentSum.add(amount);
                groupSum = groupSum.add(amount);
            }
        }

        private void endPaymentInformation() {
            handler.paymentInformation(new PaymentInformation(paymentInformationPath, paymentInformationId, debtorIban,
                    debtorAgent == null ? null : debtorAgent.build(), requestedExecutionDate));
            handler.totals(new Totals(paymentInformationPath, paymentNumberOfTransactions, paymentControlSum,
                    paymentCount, paymentSum));
        }
    }

    /**
     * Collects the shape of one debtor or creditor postal address while it is streamed.
     */
    private static final class AddressBuilder {

        private int addressLineCount;
        private boolean townName;
        private boolean country;
        private boolean otherElements;

        void element(String localName) {
            switch (localName) {
                case "AdrLine" -> addressLineCount++;
                case "Ctry" -> country = true;
                case "TwnNm" -> {
                    townName = true;
                    otherElements = true;
                }
                default -> otherElements = true;
            }
        }

        PostalAddress build(String path, Party party, LocalDate requestedExecutionDate) {
            return new PostalAddress(path, party, requestedExecutionDate, addressLineCount, townName, country,
                    otherElements);
        }
    }

    /**
     * Collects one agent while it is streamed; element paths are relative to DbtrAgt or CdtrAgt.
     */
    private static final class AgentBuilder {

        private String bic;
        private String otherIdentification;
        private boolean furtherDetails;

        void element(String relativePath) {
            if (!relativePath.equals("FinInstnId") && !relativePath.equals("FinInstnId/BICFI")) {
                furtherDetails = true;
            }
        }

        Agent build() {
            return new Agent(bic, otherIdentification, furtherDetails);
        }
    }
}
