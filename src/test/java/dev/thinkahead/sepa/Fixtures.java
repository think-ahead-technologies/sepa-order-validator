// ABOUTME: Loads real pain.001 sample files from test resources for use across all test levels.
// ABOUTME: Tests derive variants from the valid sample by targeted text replacement, with a future execution date.
package dev.thinkahead.sepa;

import dev.thinkahead.sepa.validation.rules.TargetCalendar;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public final class Fixtures {

    public static final String VALID_PAIN_001 = load("/fixtures/valid-pain.001.001.09.xml");
    public static final String SAMPLE_MESSAGE_ID = "MSG-2026-10-31-001";
    public static final String SAMPLE_PAYMENT_INFORMATION_ID = "PMT-001";
    public static final LocalDate SAMPLE_EXECUTION_DATE = LocalDate.of(2026, 11, 2);

    /**
     * A DK XML container with two copies of the sample (MsgIds MSG-CONTAINER-HASH-1 and -2, PmtInfIds PMT-001 and
     * PMT-002, executed 2099-06-01). The first carries the SHA-256 over its C14N form in the container's context, computed
     * with libxml2 (xmllint --c14n); the second has no hash. Use it once per test run, so its ids are not rejected as
     * duplicates.
     */
    public static final String CONTAINER_WITH_HASH = load("/fixtures/container-with-hash.xml");

    public static final String CONTAINER_HASH = "EC12F3500C2EE1CA21877AFA5299E151405CE0419D235253C08017F311D3E521";

    private Fixtures() {}

    /**
     * A fresh MsgId (33 characters, within ISO Max35Text), so uploads are never rejected as duplicates by accident.
     */
    public static String uniqueMessageId() {
        return "M" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * The valid sample with the given MsgId and the PmtInfId {@link #paymentInformationId(String)}, executed on
     * {@link #futureExecutionDate()} so it passes DT01 whenever the tests run.
     */
    public static String validPain001(String messageId) {
        return validPain001(messageId, futureExecutionDate());
    }

    public static String validPain001(String messageId, LocalDate executionDate) {
        return VALID_PAIN_001
                .replace("<MsgId>" + SAMPLE_MESSAGE_ID + "</MsgId>", "<MsgId>" + messageId + "</MsgId>")
                .replace(
                        "<PmtInfId>" + SAMPLE_PAYMENT_INFORMATION_ID + "</PmtInfId>",
                        "<PmtInfId>" + paymentInformationId(messageId) + "</PmtInfId>")
                .replace("<Dt>" + SAMPLE_EXECUTION_DATE + "</Dt>", "<Dt>" + executionDate + "</Dt>");
    }

    /**
     * The PmtInfId that {@link #validPain001(String)} gives a file, derived from its MsgId (at most 35 characters), so
     * a fresh MsgId also brings a fresh PmtInfId and uploads are not rejected with DU02 by accident.
     */
    public static String paymentInformationId(String messageId) {
        String id = "P" + messageId;
        return id.length() <= 35 ? id : id.substring(0, 35);
    }

    /**
     * A valid file with the given MsgId and one copy of the sample's payment information block per given PmtInfId, in
     * that order, with the group header totals adjusted.
     */
    public static String validPain001WithPaymentInformationIds(String messageId, String... paymentInformationIds) {
        String xml = validPain001(messageId);
        String block = paymentInformationBlock(xml);
        var blocks = new StringBuilder();
        for (String id : paymentInformationIds) {
            if (!blocks.isEmpty()) {
                blocks.append("\n    ");
            }
            blocks.append(block.replace(
                    "<PmtInfId>" + paymentInformationId(messageId) + "</PmtInfId>", "<PmtInfId>" + id + "</PmtInfId>"));
        }
        int count = paymentInformationIds.length;
        return xml.replace(block, blocks.toString())
                .replace(
                        "<NbOfTxs>2</NbOfTxs>\n      <CtrlSum>150.50</CtrlSum>\n      <InitgPty>",
                        "<NbOfTxs>" + 2 * count + "</NbOfTxs>\n      <CtrlSum>"
                                + new BigDecimal("150.50").multiply(BigDecimal.valueOf(count))
                                + "</CtrlSum>\n      <InitgPty>");
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
            body.append(transaction.replace(
                            "<EndToEndId>E2E-001</EndToEndId>", "<EndToEndId>E2E-" + i + "</EndToEndId>"))
                    .append('\n');
        }
        String sum = new java.math.BigDecimal("100.00")
                .multiply(java.math.BigDecimal.valueOf(transactions))
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
     * The valid sample with an unstructured creditor address in the second transaction (RR03); the first transaction
     * lacks its creditor name (RR03).
     */
    public static String withCreditorNameAndAddressViolations(String messageId) {
        return withUnstructuredCreditorAddress(validPain001(messageId)).replace("<Nm>Lieferant AG</Nm>", "");
    }

    /**
     * The given sample with the second transaction's creditor address made unstructured: only two AdrLine and Ctry,
     * no TwnNm (RR03 at any execution date).
     */
    public static String withUnstructuredCreditorAddress(String pain001) {
        return pain001.replace("<TwnNm>Paris</TwnNm>", "")
                .replace(
                        "<AdrLine>12 Rue de la Paix</AdrLine>",
                        "<AdrLine>12 Rue de la Paix</AdrLine><AdrLine>75002 Paris</AdrLine>");
    }

    /**
     * A SEPA Instant file whose requested execution date is the given past date, meaning immediate execution, with an
     * unstructured creditor address in the second transaction (RR03).
     */
    public static String pastDatedInstantWithUnstructuredCreditorAddress(String messageId, LocalDate executionDate) {
        return withUnstructuredCreditorAddress(validInstantPain001(messageId, executionDate));
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
        return validPain001(messageId, executionDate)
                .replace("</SvcLvl>", "</SvcLvl>\n        <LclInstrm>\n          <Cd>INST</Cd>\n        </LclInstrm>");
    }

    /**
     * A file mixing schemes: the sample's payment information block as SEPA Instant, followed by a copy as plain SEPA
     * credit transfer with its own PmtInfId, which DK forbids within one file (AG02 at PmtInf[2]).
     */
    public static String withMixedSchemes(String messageId) {
        String instant = validInstantPain001(messageId, futureExecutionDate());
        String sct = validPain001(messageId);
        String instantBlock = paymentInformationBlock(instant);
        String sctBlock = paymentInformationBlock(sct)
                .replace(
                        "<PmtInfId>" + paymentInformationId(messageId) + "</PmtInfId>",
                        "<PmtInfId>" + paymentInformationId(messageId).replaceFirst("^P", "S") + "</PmtInfId>");
        return instant.replace(instantBlock, instantBlock + "\n    " + sctBlock)
                .replace(
                        "<NbOfTxs>2</NbOfTxs>\n      <CtrlSum>150.50</CtrlSum>\n      <InitgPty>",
                        "<NbOfTxs>4</NbOfTxs>\n      <CtrlSum>301.00</CtrlSum>\n      <InitgPty>");
    }

    private static String paymentInformationBlock(String xml) {
        return xml.substring(xml.indexOf("<PmtInf>"), xml.indexOf("</PmtInf>") + "</PmtInf>".length());
    }

    /**
     * A DK XML container (DFÜ-Abkommen Anlage 3 V3.9, 9.1) holding the given pain.001 messages, laid out like the DK's
     * own example: container namespace as default, xsi declared on the root, each message without XML declaration.
     */
    public static String container(String... messages) {
        var xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <conxml xmlns="urn:conxml:xsd:container.nnn.001.GBIC5" \
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                xsi:schemaLocation="urn:conxml:xsd:container.nnn.001.GBIC5 container.nnn.001.GBIC5.xsd">
                  <ContainerId>
                    <SenderId>MUSTERGMBH</SenderId>
                    <IdType>SFTP</IdType>
                    <TimeStamp>115500000</TimeStamp>
                  </ContainerId>
                  <CreDtTm>2026-10-31T11:55:00.000Z</CreDtTm>
                """);
        for (String message : messages) {
            xml.append("  <MsgPain001>\n")
                    .append(message.replaceFirst("^<\\?xml[^>]*>\\s*", ""))
                    .append("  </MsgPain001>\n");
        }
        return xml.append("</conxml>\n").toString();
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
