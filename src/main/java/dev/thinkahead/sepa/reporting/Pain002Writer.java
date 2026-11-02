// ABOUTME: Writes pain.002.001.10 customer payment status reports with StAX.
// ABOUTME: Each violation becomes a group-level StsRsnInf with its reason code, location and message.
package dev.thinkahead.sepa.reporting;

import dev.thinkahead.sepa.validation.Violation;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.springframework.stereotype.Component;

@Component
class Pain002Writer {

    static final String MESSAGE_NAME_ID = "pain.002.001.10";

    private static final String NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:" + MESSAGE_NAME_ID;
    private static final String NOT_PROVIDED = "NOTPROVIDED";
    private static final int MAX_ADDITIONAL_INFORMATION = 105;

    byte[] write(StatusReport report) {
        var out = new ByteArrayOutputStream();
        try {
            XMLStreamWriter xml =
                    XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(out, StandardCharsets.UTF_8.name());
            xml.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
            xml.writeStartElement("Document");
            xml.writeDefaultNamespace(NAMESPACE);
            xml.writeStartElement("CstmrPmtStsRpt");

            xml.writeStartElement("GrpHdr");
            element(xml, "MsgId", report.messageId());
            element(
                    xml,
                    "CreDtTm",
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                            report.creationDateTime().truncatedTo(ChronoUnit.SECONDS)));
            xml.writeEndElement();

            xml.writeStartElement("OrgnlGrpInfAndSts");
            element(xml, "OrgnlMsgId", orNotProvided(report.originalMessageId()));
            element(xml, "OrgnlMsgNmId", orNotProvided(report.originalMessageNameId()));
            element(xml, "GrpSts", report.groupStatus().name());
            for (Violation violation : report.reasons()) {
                xml.writeStartElement("StsRsnInf");
                xml.writeStartElement("Rsn");
                element(xml, "Cd", violation.reasonCode());
                xml.writeEndElement();
                element(xml, "AddtlInf", truncate(violation.path()));
                element(xml, "AddtlInf", truncate(violation.message()));
                xml.writeEndElement();
            }
            xml.writeEndElement();

            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Cannot write pain.002 status report " + report.messageId(), e);
        }
        return out.toByteArray();
    }

    private static void element(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        xml.writeStartElement(name);
        xml.writeCharacters(value);
        xml.writeEndElement();
    }

    private static String orNotProvided(String value) {
        return value == null || value.isBlank() ? NOT_PROVIDED : value;
    }

    private static String truncate(String value) {
        if (value.codePointCount(0, value.length()) <= MAX_ADDITIONAL_INFORMATION) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, MAX_ADDITIONAL_INFORMATION));
    }
}
