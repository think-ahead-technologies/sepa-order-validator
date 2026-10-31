// ABOUTME: Loads real pain.001 sample files from test resources for use across all test levels.
// ABOUTME: Tests derive variants from the valid sample by targeted text replacement, with a future execution date.
package dev.thinkahead.sepa;

import dev.thinkahead.sepa.validation.rules.TargetCalendar;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public final class Fixtures {

    public static final String VALID_PAIN_001 = load("/fixtures/valid-pain.001.001.09.xml");
    public static final String SAMPLE_MESSAGE_ID = "MSG-2026-10-31-001";
    public static final LocalDate SAMPLE_EXECUTION_DATE = LocalDate.of(2026, 11, 2);

    private Fixtures() {
    }

    /**
     * A fresh MsgId (33 characters, within ISO Max35Text), so uploads are never rejected as duplicates by accident.
     */
    public static String uniqueMessageId() {
        return "M" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * The valid sample with the given MsgId, executed on {@link #futureExecutionDate()} so it passes DT01 whenever the
     * tests run.
     */
    public static String validPain001(String messageId) {
        return validPain001(messageId, futureExecutionDate());
    }

    public static String validPain001(String messageId, LocalDate executionDate) {
        return VALID_PAIN_001
                .replace("<MsgId>" + SAMPLE_MESSAGE_ID + "</MsgId>", "<MsgId>" + messageId + "</MsgId>")
                .replace("<Dt>" + SAMPLE_EXECUTION_DATE + "</Dt>", "<Dt>" + executionDate + "</Dt>");
    }

    /**
     * The first TARGET business day at least two days after today in Frankfurt.
     */
    public static LocalDate futureExecutionDate() {
        LocalDate date = LocalDate.now(ZoneId.of("Europe/Berlin")).plusDays(2);
        while (!TargetCalendar.isBusinessDay(date)) {
            date = date.plusDays(1);
        }
        return date;
    }

    /**
     * Pads a file with an XML comment until it is at least the given size; the content stays schema-valid.
     */
    public static String paddedTo(String xml, int minimumBytes) {
        int padding = Math.max(0, minimumBytes - xml.length());
        return xml.replace("</Document>", "<!--" + "x".repeat(padding) + "--></Document>");
    }

    /**
     * A schema-valid pain.001 with the given number of credit transfers, each to an invalid creditor IBAN.
     */
    public static String withInvalidCreditorIbans(String messageId, int transactions) {
        String xml = validPain001(messageId);
        int firstTransaction = xml.indexOf("<CdtTrfTxInf>");
        String transaction = xml.substring(firstTransaction, xml.indexOf("</CdtTrfTxInf>") + "</CdtTrfTxInf>".length())
                .replace("NL91ABNA0417164300", "NL91ABNA0417164301");
        var body = new StringBuilder();
        for (int i = 1; i <= transactions; i++) {
            body.append(transaction.replace("<EndToEndId>E2E-001</EndToEndId>", "<EndToEndId>E2E-" + i + "</EndToEndId>"))
                    .append('\n');
        }
        String sum = new java.math.BigDecimal("100.00").multiply(java.math.BigDecimal.valueOf(transactions))
                .toPlainString();
        return (xml.substring(0, firstTransaction) + body + xml.substring(xml.indexOf("</PmtInf>")))
                .replace("<NbOfTxs>2</NbOfTxs>", "<NbOfTxs>" + transactions + "</NbOfTxs>")
                .replace("<CtrlSum>150.50</CtrlSum>", "<CtrlSum>" + sum + "</CtrlSum>");
    }

    public static final String LONG_CREDITOR_NAME =
            "Gesellschaft für Informationstechnologie und Datenverarbeitung mbH & Co. KG";

    /**
     * A schema-valid pain.001 breaking the SEPA field rules that the ISO schema does not enforce: a zero amount (AM01)
     * and a creditor agent without BIC (RC01) in the first transaction; three decimal places (AM12), a creditor name
     * over 70 characters (FF01) and both unstructured and structured remittance information (FF01) in the second.
     */
    public static String withSepaFieldRuleViolations(String messageId) {
        return validPain001(messageId)
                .replace("Ccy=\"EUR\">100.00<", "Ccy=\"EUR\">0.00<")
                .replace("Ccy=\"EUR\">50.50<", "Ccy=\"EUR\">50.505<")
                .replace("<CtrlSum>150.50</CtrlSum>", "<CtrlSum>50.505</CtrlSum>")
                .replace("<BICFI>ABNANL2A</BICFI>", "<ClrSysMmbId><MmbId>12345</MmbId></ClrSysMmbId>")
                .replace("<Nm>Fournisseur SA</Nm>", "<Nm>" + LONG_CREDITOR_NAME.replace("&", "&amp;") + "</Nm>")
                .replaceFirst("<RmtInf>(\\s*)<Strd>", "<RmtInf>$1<Ustrd>Facture 0815</Ustrd>$1<Strd>");
    }

    /**
     * The valid sample executed on 23 November 2026 (the first business day on which SEPA allows only structured and
     * hybrid addresses) or later, with an unstructured creditor address in the second transaction (RR03); the first
     * transaction lacks its creditor name (RR03).
     */
    public static String withCreditorNameAndAddressViolations(String messageId) {
        LocalDate firstBusinessDayWithStructuredAddresses = LocalDate.of(2026, 11, 23);
        LocalDate future = futureExecutionDate();
        return validPain001(messageId,
                future.isAfter(firstBusinessDayWithStructuredAddresses) ? future : firstBusinessDayWithStructuredAddresses)
                .replace("<TwnNm>Paris</TwnNm>", "")
                .replace("<AdrLine>12 Rue de la Paix</AdrLine>",
                        "<AdrLine>12 Rue de la Paix</AdrLine><AdrLine>75002 Paris</AdrLine>")
                .replace("<Nm>Lieferant AG</Nm>", "");
    }

    public static final String FRENCH_CREDITOR_NAME = "Société Générale";

    /**
     * The valid sample with characters outside the DK character set: an umlaut in the first transaction's EndToEndId
     * (identifiers allow only basic Latin) and an accented creditor name in the second transaction, both CH16.
     */
    public static String withCharacterSetViolations(String messageId) {
        return validPain001(messageId)
                .replace("<EndToEndId>E2E-001</EndToEndId>", "<EndToEndId>E2E-Müller-001</EndToEndId>")
                .replace("<Nm>Fournisseur SA</Nm>", "<Nm>" + FRENCH_CREDITOR_NAME + "</Nm>");
    }

    /**
     * The valid sample as SEPA Instant credit transfer: local instrument INST on payment information level.
     */
    public static String validInstantPain001(String messageId, LocalDate executionDate) {
        return validPain001(messageId, executionDate).replace("</SvcLvl>",
                "</SvcLvl>\n        <LclInstrm>\n          <Cd>INST</Cd>\n        </LclInstrm>");
    }

    /**
     * A file mixing schemes: the sample's payment information block as SEPA Instant, followed by a copy as plain SEPA
     * credit transfer (PMT-002), which DK forbids within one file (AG02 at PmtInf[2]).
     */
    public static String withMixedSchemes(String messageId) {
        String instant = validInstantPain001(messageId, futureExecutionDate());
        String sct = validPain001(messageId);
        String instantBlock = paymentInformationBlock(instant);
        String sctBlock = paymentInformationBlock(sct).replace("<PmtInfId>PMT-001</PmtInfId>",
                "<PmtInfId>PMT-002</PmtInfId>");
        return instant
                .replace(instantBlock, instantBlock + "\n    " + sctBlock)
                .replace("<NbOfTxs>2</NbOfTxs>\n      <CtrlSum>150.50</CtrlSum>\n      <InitgPty>",
                        "<NbOfTxs>4</NbOfTxs>\n      <CtrlSum>301.00</CtrlSum>\n      <InitgPty>");
    }

    private static String paymentInformationBlock(String xml) {
        return xml.substring(xml.indexOf("<PmtInf>"), xml.indexOf("</PmtInf>") + "</PmtInf>".length());
    }

    public static byte[] bytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private static String load(String resource) {
        try (InputStream in = Fixtures.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
