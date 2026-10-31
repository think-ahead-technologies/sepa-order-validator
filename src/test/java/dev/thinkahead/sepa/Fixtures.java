// ABOUTME: Loads real pain.001 sample files from test resources for use across all test levels.
// ABOUTME: Tests derive invalid variants from the valid sample by targeted text replacement.
package dev.thinkahead.sepa;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class Fixtures {

    public static final String VALID_PAIN_001 = load("/fixtures/valid-pain.001.001.09.xml");
    public static final String SAMPLE_MESSAGE_ID = "MSG-2026-10-31-001";

    private Fixtures() {
    }

    /**
     * A fresh MsgId (33 characters, within ISO Max35Text), so uploads are never rejected as duplicates by accident.
     */
    public static String uniqueMessageId() {
        return "M" + UUID.randomUUID().toString().replace("-", "");
    }

    public static String validPain001(String messageId) {
        return VALID_PAIN_001.replace("<MsgId>" + SAMPLE_MESSAGE_ID + "</MsgId>", "<MsgId>" + messageId + "</MsgId>");
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
