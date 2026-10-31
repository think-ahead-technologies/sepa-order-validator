// ABOUTME: Unit tests for pain.002.001.10 status report generation.
// ABOUTME: Every generated report is validated against the official ISO 20022 XSD.
package dev.thinkahead.sepa.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.thinkahead.sepa.validation.Violation;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

class Pain002WriterTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 31, 14, 30, 0, 0, ZoneOffset.UTC);

    private final Pain002Writer writer = new Pain002Writer();

    @Test
    void writesSchemaValidAcceptanceReport() throws Exception {
        var report = new StatusReport("STS-1", NOW, "MSG-2026-10-31-001", "pain.001.001.09",
                GroupStatus.ACCP, List.of());

        byte[] xml = writer.write(report);

        assertSchemaValid(xml);
        Document doc = parse(xml);
        assertThat(text(doc, "GrpHdr/MsgId")).isEqualTo("STS-1");
        assertThat(text(doc, "GrpHdr/CreDtTm")).isEqualTo("2026-10-31T14:30:00Z");
        assertThat(text(doc, "OrgnlGrpInfAndSts/OrgnlMsgId")).isEqualTo("MSG-2026-10-31-001");
        assertThat(text(doc, "OrgnlGrpInfAndSts/OrgnlMsgNmId")).isEqualTo("pain.001.001.09");
        assertThat(text(doc, "OrgnlGrpInfAndSts/GrpSts")).isEqualTo("ACCP");
        assertThat(nodes(doc, "OrgnlGrpInfAndSts/StsRsnInf").getLength()).isZero();
    }

    @Test
    void writesOneReasonPerViolationInRejectionReport() throws Exception {
        var report = new StatusReport("STS-2", NOW, "MSG-2026-10-31-001", "pain.001.001.09", GroupStatus.RJCT,
                List.of(new Violation("AC03", "PmtInf[1]/CdtTrfTxInf[1]/CdtrAcct/Id/IBAN", "Invalid IBAN"),
                        new Violation("AM03", "PmtInf[1]/CdtTrfTxInf[2]/Amt/InstdAmt/@Ccy", "Not EUR")));

        byte[] xml = writer.write(report);

        assertSchemaValid(xml);
        Document doc = parse(xml);
        assertThat(text(doc, "OrgnlGrpInfAndSts/GrpSts")).isEqualTo("RJCT");
        assertThat(text(doc, "OrgnlGrpInfAndSts/StsRsnInf[1]/Rsn/Cd")).isEqualTo("AC03");
        assertThat(text(doc, "OrgnlGrpInfAndSts/StsRsnInf[1]/AddtlInf[1]"))
                .isEqualTo("PmtInf[1]/CdtTrfTxInf[1]/CdtrAcct/Id/IBAN");
        assertThat(text(doc, "OrgnlGrpInfAndSts/StsRsnInf[1]/AddtlInf[2]")).isEqualTo("Invalid IBAN");
        assertThat(text(doc, "OrgnlGrpInfAndSts/StsRsnInf[2]/Rsn/Cd")).isEqualTo("AM03");
    }

    @Test
    void truncatesAdditionalInformationToMax105Characters() throws Exception {
        String longMessage = "x".repeat(300);
        var report = new StatusReport("STS-3", NOW, "MSG-1", "pain.001.001.09", GroupStatus.RJCT,
                List.of(new Violation("FF01", "Line 1, column 1", longMessage)));

        byte[] xml = writer.write(report);

        assertSchemaValid(xml);
        assertThat(text(parse(xml), "OrgnlGrpInfAndSts/StsRsnInf[1]/AddtlInf[2]")).hasSize(105);
    }

    @Test
    void usesNotProvidedWhenOriginalMessageIsUnknown() throws Exception {
        var report = new StatusReport("STS-4", NOW, null, null, GroupStatus.RJCT,
                List.of(new Violation("FF01", "Document", "Unsupported or unreadable message format")));

        byte[] xml = writer.write(report);

        assertSchemaValid(xml);
        Document doc = parse(xml);
        assertThat(text(doc, "OrgnlGrpInfAndSts/OrgnlMsgId")).isEqualTo("NOTPROVIDED");
        assertThat(text(doc, "OrgnlGrpInfAndSts/OrgnlMsgNmId")).isEqualTo("NOTPROVIDED");
    }

    @Test
    void escapesXmlSpecialCharactersInMessages() throws Exception {
        var report = new StatusReport("STS-5", NOW, "MSG-<&>", "pain.001.001.09", GroupStatus.RJCT,
                List.of(new Violation("FF01", "Line 1, column 1", "Expected '<MsgId>' & more")));

        byte[] xml = writer.write(report);

        assertSchemaValid(xml);
        Document doc = parse(xml);
        assertThat(text(doc, "OrgnlGrpInfAndSts/OrgnlMsgId")).isEqualTo("MSG-<&>");
        assertThat(text(doc, "OrgnlGrpInfAndSts/StsRsnInf[1]/AddtlInf[2]")).isEqualTo("Expected '<MsgId>' & more");
    }

    static void assertSchemaValid(byte[] xml) {
        assertThatCode(() -> SchemaFactory.newDefaultInstance()
                .newSchema(Pain002WriterTest.class.getResource("/xsd/pain.002.001.10.xsd"))
                .newValidator()
                .validate(new StreamSource(new ByteArrayInputStream(xml))))
                .doesNotThrowAnyException();
    }

    static Document parse(byte[] xml) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    static String text(Document doc, String relativePath) throws Exception {
        return XPathFactory.newDefaultInstance().newXPath().evaluate(xpath(relativePath), doc);
    }

    private static NodeList nodes(Document doc, String relativePath) throws Exception {
        return (NodeList) XPathFactory.newDefaultInstance().newXPath()
                .evaluate(xpath(relativePath), doc, XPathConstants.NODESET);
    }

    private static String xpath(String relativePath) {
        StringBuilder xpath = new StringBuilder("/*[local-name()='Document']/*[local-name()='CstmrPmtStsRpt']");
        for (String step : relativePath.split("/")) {
            int bracket = step.indexOf('[');
            String name = bracket < 0 ? step : step.substring(0, bracket);
            String predicate = bracket < 0 ? "" : step.substring(bracket);
            xpath.append("/*[local-name()='").append(name).append("']").append(predicate);
        }
        return xpath.toString();
    }
}
