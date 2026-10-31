// ABOUTME: Detects the message format of a payment file from the namespace of its root element.
// ABOUTME: Files that are malformed or contain a DOCTYPE are treated as unreadable.
package dev.thinkahead.sepa.validation.xml;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class FormatDetector {

    public Optional<MessageFormat> detect(byte[] content) {
        try {
            XMLStreamReader reader = SecureXml.inputFactory().createXMLStreamReader(new ByteArrayInputStream(content));
            try {
                while (reader.hasNext()) {
                    switch (reader.next()) {
                        case XMLStreamConstants.DTD:
                            return Optional.empty();
                        case XMLStreamConstants.START_ELEMENT:
                            return MessageFormat.byNamespace(reader.getNamespaceURI());
                        default:
                            break;
                    }
                }
                return Optional.empty();
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            return Optional.empty();
        }
    }
}
