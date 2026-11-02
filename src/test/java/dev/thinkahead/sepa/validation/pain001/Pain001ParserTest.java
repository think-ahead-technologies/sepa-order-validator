// ABOUTME: Unit tests for the streaming pain.001.001.09 parser.
// ABOUTME: Verifies the emitted group header, payment information, transactions, parties and computed totals.
package dev.thinkahead.sepa.validation.pain001;

import static dev.thinkahead.sepa.Fixtures.VALID_PAIN_001;
import static dev.thinkahead.sepa.Fixtures.bytes;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
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

        // <CdtrRefInf><Tp><CdOrPrtry><Cd>SCOR</Cd></CdOrPrtry><Issr>ISO</Issr></Tp><Ref>RF18539007547034</Ref>
        // </CdtrRefInf> without whitespace between the tags is 113 characters
        assertThat(handler.transactions).containsExactly(
                new CreditTransferTransaction("PmtInf[1]/CdtTrfTxInf[1]", "E2E-001",
                        new BigDecimal("100.00"), "EUR", "NL91ABNA0417164300",
                        new Agent("ABNANL2A", null, false),
                        new RemittanceInformation(1, 0, 0, null), "Lieferant AG"),
                new CreditTransferTransaction("PmtInf[1]/CdtTrfTxInf[2]", "E2E-002",
                        new BigDecimal("50.50"), "EUR", "FR1420041010050500013M02606",
                        null,
                        new RemittanceInformation(0, 1, 113,
                                new CreditorReference(true, "SCOR", "RF18539007547034")), "Fournisseur SA"));
    }

    @Test
    void emitsTransactionWithoutCreditorName() {
        var xml = VALID_PAIN_001.replace("<Nm>Lieferant AG</Nm>", "");

        new Pain001Parser().parse(bytes(xml), handler);

        assertThat(handler.transactions.getFirst().creditorName()).isNull();
    }

    @Test
    void emitsDebtorAndCreditorAddresses() {
        new Pain001Parser().parse(bytes(VALID_PAIN_001), handler);

        assertThat(handler.postalAddresses).containsExactly(
                new PostalAddress("PmtInf[1]/Dbtr/PstlAdr", Party.DEBTOR, 0, true, true),
                new PostalAddress("PmtInf[1]/CdtTrfTxInf[1]/Cdtr/PstlAdr", Party.CREDITOR, 1, true, true),
                new PostalAddress("PmtInf[1]/CdtTrfTxInf[2]/Cdtr/PstlAdr", Party.CREDITOR, 1, true, true));
    }

    @Test
    void takesTheRequestedExecutionDateFromADateTime() {
        var xml = VALID_PAIN_001.replace("<Dt>2026-11-02</Dt>", "<DtTm>2026-11-23T08:00:00+01:00</DtTm>");

        new Pain001Parser().parse(bytes(xml), handler);

        assertThat(handler.paymentInformation.getFirst().requestedExecutionDate())
                .isEqualTo(LocalDate.of(2026, 11, 23));
    }

    @Test
    void ignoresAddressesOfUltimatePartiesAndAgents() {
        var xml = VALID_PAIN_001.replace("<ChrgBr>SLEV</ChrgBr>",
                "<UltmtDbtr><Nm>Muster Holding</Nm><PstlAdr><AdrLine>Postfach 1</AdrLine></PstlAdr></UltmtDbtr>"
                        + "<ChrgBr>SLEV</ChrgBr>");

        new Pain001Parser().parse(bytes(xml), handler);

        assertThat(handler.postalAddresses).extracting(PostalAddress::path).containsExactly(
                "PmtInf[1]/Dbtr/PstlAdr",
                "PmtInf[1]/CdtTrfTxInf[1]/Cdtr/PstlAdr",
                "PmtInf[1]/CdtTrfTxInf[2]/Cdtr/PstlAdr");
    }

    @Test
    void emitsTransactionWithoutRemittanceInformation() {
        var xml = VALID_PAIN_001.replaceFirst("<RmtInf>\\s*<Ustrd>Rechnung 4711</Ustrd>\\s*</RmtInf>", "");

        new Pain001Parser().parse(bytes(xml), handler);

        assertThat(handler.transactions.getFirst().remittanceInformation()).isEqualTo(RemittanceInformation.NONE);
    }

    @Test
    void countsEveryOccurrenceOfUnstructuredAndStructuredRemittanceInformation() {
        var xml = VALID_PAIN_001.replace("<Ustrd>Rechnung 4711</Ustrd>",
                "<Ustrd>Rechnung 4711</Ustrd><Ustrd>Rechnung 4712</Ustrd>"
                        + "<Strd><AddtlRmtInf>Teil 1</AddtlRmtInf></Strd>"
                        + "<Strd><AddtlRmtInf>Teil 2 und 3</AddtlRmtInf></Strd>");

        new Pain001Parser().parse(bytes(xml), handler);

        // <AddtlRmtInf>Teil 2 und 3</AddtlRmtInf> is 13 + 12 + 14 = 39 characters
        assertThat(handler.transactions.getFirst().remittanceInformation())
                .isEqualTo(new RemittanceInformation(2, 2, 39, null));
    }

    @Test
    void countsAttributesTowardsTheLengthOfStructuredRemittanceInformation() {
        var xml = VALID_PAIN_001.replace("<Ustrd>Rechnung 4711</Ustrd>",
                "<Strd><RfrdDocAmt><DuePyblAmt Ccy=\"EUR\">100.00</DuePyblAmt></RfrdDocAmt></Strd>");

        new Pain001Parser().parse(bytes(xml), handler);

        // <RfrdDocAmt> 12, <DuePyblAmt Ccy="EUR"> 22, 100.00 6, </DuePyblAmt> 13, </RfrdDocAmt> 13
        assertThat(handler.transactions.getFirst().remittanceInformation().longestStructuredLength())
                .isEqualTo(66);
    }

    @Test
    void emitsCreditorReferenceWithoutType() {
        var xml = VALID_PAIN_001.replaceFirst("(?s)<Tp>.*?</Tp>", "");

        new Pain001Parser().parse(bytes(xml), handler);

        assertThat(handler.transactions.get(1).remittanceInformation().creditorReference())
                .isEqualTo(new CreditorReference(false, null, "RF18539007547034"));
    }

    @Test
    void marksCreditorAgentWithDetailsBeyondTheBic() {
        var xml = VALID_PAIN_001.replace("<BICFI>ABNANL2A</BICFI>",
                "<ClrSysMmbId><MmbId>12345</MmbId></ClrSysMmbId>");

        new Pain001Parser().parse(bytes(xml), handler);

        assertThat(handler.transactions.getFirst().creditorAgent()).isEqualTo(new Agent(null, null, true));
    }

    @Test
    void emitsDebtorAgentIdentifiedByOtherIdentification() {
        var xml = VALID_PAIN_001.replace("<BICFI>COBADEFFXXX</BICFI>", "<Othr><Id>NOTPROVIDED</Id></Othr>");

        new Pain001Parser().parse(bytes(xml), handler);

        assertThat(handler.paymentInformation.getFirst().debtorAgent())
                .isEqualTo(new Agent(null, "NOTPROVIDED", true));
    }

    @Test
    void emitsEveryPartyNameWithLocation() {
        var xml = VALID_PAIN_001
                .replace("<ChrgBr>SLEV</ChrgBr>", "<UltmtDbtr><Nm>Muster Holding</Nm></UltmtDbtr><ChrgBr>SLEV</ChrgBr>")
                .replaceFirst("</CdtrAcct>", "</CdtrAcct><UltmtCdtr><Nm>Lieferant Holding</Nm></UltmtCdtr>")
                .replaceFirst("(50\\.50</InstdAmt>\\s*</Amt>)", "$1<UltmtDbtr><Nm>Muster Filiale</Nm></UltmtDbtr>");

        new Pain001Parser().parse(bytes(xml), handler);

        assertThat(handler.partyNames).containsExactly(
                new PartyName("GrpHdr/InitgPty/Nm", "Muster GmbH"),
                new PartyName("PmtInf[1]/Dbtr/Nm", "Muster GmbH"),
                new PartyName("PmtInf[1]/UltmtDbtr/Nm", "Muster Holding"),
                new PartyName("PmtInf[1]/CdtTrfTxInf[1]/Cdtr/Nm", "Lieferant AG"),
                new PartyName("PmtInf[1]/CdtTrfTxInf[1]/UltmtCdtr/Nm", "Lieferant Holding"),
                new PartyName("PmtInf[1]/CdtTrfTxInf[2]/UltmtDbtr/Nm", "Muster Filiale"),
                new PartyName("PmtInf[1]/CdtTrfTxInf[2]/Cdtr/Nm", "Fournisseur SA"));
    }

    @Test
    void emitsTheTextOfEveryLeafElementWithLocation() {
        new Pain001Parser().parse(bytes(VALID_PAIN_001), handler);

        assertThat(handler.elementTexts).contains(
                new ElementText("GrpHdr/MsgId", "MSG-2026-10-31-001"),
                new ElementText("GrpHdr/InitgPty/Nm", "Muster GmbH"),
                new ElementText("PmtInf[1]/PmtInfId", "PMT-001"),
                new ElementText("PmtInf[1]/Dbtr/PstlAdr/StrtNm", "Hauptstraße"),
                new ElementText("PmtInf[1]/CdtTrfTxInf[1]/PmtId/EndToEndId", "E2E-001"),
                new ElementText("PmtInf[1]/CdtTrfTxInf[2]/Cdtr/Nm", "Fournisseur SA"),
                new ElementText("PmtInf[1]/CdtTrfTxInf[2]/RmtInf/Strd/CdtrRefInf/Ref", "RF18539007547034"));
        assertThat(handler.elementTexts)
                .as("elements that only contain other elements carry no text")
                .noneSatisfy(text -> assertThat(text.path()).endsWith("/Cdtr"))
                .allSatisfy(text -> assertThat(text.value()).isNotBlank());
    }

    @Test
    void emitsElementTextWithEntitiesResolved() {
        var xml = VALID_PAIN_001.replace("<Nm>Lieferant AG</Nm>", "<Nm>Lieferant &amp; S&#246;hne</Nm>");

        new Pain001Parser().parse(bytes(xml), handler);

        assertThat(handler.elementTexts)
                .contains(new ElementText("PmtInf[1]/CdtTrfTxInf[1]/Cdtr/Nm", "Lieferant & Söhne"));
    }

    @Test
    void emitsPaymentTypeInformationOfEveryLevel() {
        var xml = instant(VALID_PAIN_001).replaceFirst("(<PmtId>\\s*<EndToEndId>E2E-002</EndToEndId>\\s*</PmtId>)",
                "$1<PmtTpInf><LclInstrm><Prtry>EXPRESS</Prtry></LclInstrm></PmtTpInf>");

        new Pain001Parser().parse(bytes(xml), handler);

        assertThat(handler.paymentTypes).containsExactly(
                new PaymentTypeInformation("PmtInf[1]/PmtTpInf", "INST", null, "SEPA"),
                new PaymentTypeInformation("PmtInf[1]/CdtTrfTxInf[2]/PmtTpInf", null, "EXPRESS", null));
    }

    @Test
    void classifiesPaymentInformationAsSepaInstantFromItsOwnLevel() {
        new Pain001Parser().parse(bytes(instant(VALID_PAIN_001)), handler);

        assertThat(handler.paymentInformation.getFirst().scheme()).isEqualTo(CreditTransferScheme.SCT_INST);
    }

    @Test
    void classifiesPaymentInformationAsSepaInstantFromItsTransactions() {
        String instantPaymentType = "$1<PmtTpInf><LclInstrm><Cd>INST</Cd></LclInstrm></PmtTpInf>";
        var xml = VALID_PAIN_001.replaceFirst("(?s)<PmtTpInf>.*?</PmtTpInf>", "")
                .replaceAll("(<PmtId>\\s*<EndToEndId>E2E-00[12]</EndToEndId>\\s*</PmtId>)", instantPaymentType);

        new Pain001Parser().parse(bytes(xml), handler);

        assertThat(handler.paymentInformation.getFirst().scheme()).isEqualTo(CreditTransferScheme.SCT_INST);
    }

    @Test
    void classifiesPaymentInformationWithBothSchemesAsMixed() {
        var xml = VALID_PAIN_001.replaceFirst("(?s)<PmtTpInf>.*?</PmtTpInf>", "")
                .replaceFirst("(<PmtId>\\s*<EndToEndId>E2E-002</EndToEndId>\\s*</PmtId>)",
                        "$1<PmtTpInf><LclInstrm><Cd>INST</Cd></LclInstrm></PmtTpInf>");

        new Pain001Parser().parse(bytes(xml), handler);

        assertThat(handler.paymentInformation.getFirst().scheme()).isEqualTo(CreditTransferScheme.MIXED);
    }

    private static String instant(String xml) {
        return xml.replace("</SvcLvl>", "</SvcLvl><LclInstrm><Cd>INST</Cd></LclInstrm>");
    }

    @Test
    void emitsPaymentInformation() {
        new Pain001Parser().parse(bytes(VALID_PAIN_001), handler);

        assertThat(handler.paymentInformation).containsExactly(
                new PaymentInformation("PmtInf[1]", "PMT-001", "DE89370400440532013000",
                        new Agent("COBADEFFXXX", null, false), LocalDate.of(2026, 11, 2),
                        CreditTransferScheme.SCT));
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
        final List<PartyName> partyNames = new ArrayList<>();
        final List<PostalAddress> postalAddresses = new ArrayList<>();
        final List<ElementText> elementTexts = new ArrayList<>();
        final List<PaymentTypeInformation> paymentTypes = new ArrayList<>();

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

        @Override
        public void partyName(PartyName partyName) {
            partyNames.add(partyName);
        }

        @Override
        public void postalAddress(PostalAddress postalAddress) {
            postalAddresses.add(postalAddress);
        }

        @Override
        public void elementText(ElementText elementText) {
            elementTexts.add(elementText);
        }

        @Override
        public void paymentTypeInformation(PaymentTypeInformation paymentTypeInformation) {
            paymentTypes.add(paymentTypeInformation);
        }
    }
}
