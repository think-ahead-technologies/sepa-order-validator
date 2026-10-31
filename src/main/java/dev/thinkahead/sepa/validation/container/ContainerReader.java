// ABOUTME: Reads DK XML containers (DFÜ-Abkommen Anlage 3 V3.9, 9.1): checks the envelope and splits out the messages.
// ABOUTME: Messages come out in C14N form, checked against their optional SHA-256 hash; faults are FF01.
package dev.thinkahead.sepa.validation.container;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.xml.SchemaError;
import dev.thinkahead.sepa.validation.xml.SchemaValidator;
import dev.thinkahead.sepa.validation.xml.SecureXml;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.validation.Schema;
import org.xml.sax.SAXException;

public class ContainerReader {

    static final String NAMESPACE = "urn:conxml:xsd:container.nnn.001.GBIC5";

    private static final String SCHEMA_RESOURCE = "/xsd/container.nnn.001.GBIC5.xsd";
    private static final String MESSAGE_PREFIX = "Msg";
    private static final String CREDIT_TRANSFER_MESSAGE = "MsgPain001";
    private static final String DOCUMENT = "Document";
    private static final String HASH_VALUE = "HashValue";
    private static final String INVALID_FILE_FORMAT = "FF01";
    private static final int MESSAGE_DEPTH = 2;
    private static final int DOCUMENT_DEPTH = 3;

    private final Schema schema;
    private final SchemaValidator schemaValidator;

    /**
     * @param maxErrors container schema validation stops once this many errors are collected
     */
    public ContainerReader(int maxErrors) {
        this.schemaValidator = new SchemaValidator(maxErrors);
        URL xsd = ContainerReader.class.getResource(SCHEMA_RESOURCE);
        if (xsd == null) {
            throw new IllegalStateException("Missing XSD " + SCHEMA_RESOURCE);
        }
        try {
            this.schema = SecureXml.schema(xsd, new EmbeddedDocumentStubs());
        } catch (SAXException e) {
            throw new IllegalStateException("Cannot compile XSD " + SCHEMA_RESOURCE, e);
        }
    }

    /**
     * True if the root element is a DK XML container; malformed files and files with a DOCTYPE are not containers.
     */
    public boolean isContainer(byte[] content) {
        try {
            XMLStreamReader reader = SecureXml.inputFactory().createXMLStreamReader(new ByteArrayInputStream(content));
            try {
                while (reader.hasNext()) {
                    switch (reader.next()) {
                        case XMLStreamConstants.DTD:
                            return false;
                        case XMLStreamConstants.START_ELEMENT:
                            return NAMESPACE.equals(reader.getNamespaceURI());
                        default:
                            break;
                    }
                }
                return false;
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            return false;
        }
    }

    /**
     * Checks the container against its schema and the DK rules, then splits it into its messages.
     *
     * @param content opens the container's bytes; called once per pass over the container
     */
    public ContainerReading read(Supplier<InputStream> content) {
        List<SchemaError> errors = schemaValidator.validate(schema, content.get());
        if (!errors.isEmpty()) {
            return new ContainerReading.Fault(errors.stream()
                    .map(error -> new Violation(INVALID_FILE_FORMAT,
                            "Line " + error.line() + ", column " + error.column(), "Container: " + error.message()))
                    .toList());
        }
        try {
            return split(content.get());
        } catch (XMLStreamException e) {
            return fault("Container", "Container is not readable");
        }
    }

    private ContainerReading split(InputStream content) throws XMLStreamException {
        XMLEventReader events = SecureXml.inputFactory().createXMLEventReader(content);
        try {
            List<byte[]> messages = new ArrayList<>();
            Map<String, String> containerNamespaces = new HashMap<>();
            Map<String, String> messageNamespaces = new HashMap<>();
            String hashValue = null;
            int depth = 0;
            while (events.hasNext()) {
                XMLEvent event = events.nextEvent();
                if (event.isStartDocument() && !isUtf8((StartDocument) event)) {
                    return fault("Container", "Container must be encoded in UTF-8");
                }
                if (event.isEndElement()) {
                    depth--;
                    continue;
                }
                if (!event.isStartElement()) {
                    continue;
                }
                depth++;
                StartElement element = event.asStartElement();
                String name = element.getName().getLocalPart();
                if (depth == 1) {
                    inherit(element, containerNamespaces);
                } else if (depth == MESSAGE_DEPTH && name.startsWith(MESSAGE_PREFIX)) {
                    if (!name.equals(CREDIT_TRANSFER_MESSAGE)) {
                        return fault("Container",
                                "Only credit transfer containers (" + CREDIT_TRANSFER_MESSAGE + ") are supported");
                    }
                    messageNamespaces = new HashMap<>(containerNamespaces);
                    inherit(element, messageNamespaces);
                    hashValue = null;
                } else if (depth == DOCUMENT_DEPTH && name.equals(HASH_VALUE)) {
                    hashValue = events.getElementText().strip();
                    depth--;
                } else if (depth == DOCUMENT_DEPTH && name.equals(DOCUMENT)) {
                    String path = CREDIT_TRANSFER_MESSAGE + "[" + (messages.size() + 1) + "]";
                    byte[] message = canonicalMessage(element, events, messageNamespaces);
                    depth--;
                    if (message == null) {
                        return fault(path + "/Document", "Embedded messages must not use a namespace prefix");
                    }
                    if (hashValue != null && !hashValue.equalsIgnoreCase(sha256(message))) {
                        return fault(path + "/HashValue",
                                "Hash value does not match the content of the message (SHA-256 over its C14N form)");
                    }
                    messages.add(message);
                }
            }
            return new ContainerReading.Split(messages);
        } finally {
            events.close();
        }
    }

    /**
     * Writes the message from its Document start element up to the matching end element in Canonical XML 1.0, in the
     * context of the container: the form its hash value is computed over (Anlage 3 V3.9, 9.1.1). The result is a
     * standalone UTF-8 document.
     *
     * @return the message, or null if an element of the message uses a namespace prefix
     */
    private static byte[] canonicalMessage(StartElement document, XMLEventReader events,
            Map<String, String> inheritedNamespaces) throws XMLStreamException {
        var out = new ByteArrayOutputStream();
        var canonical = new CanonicalXml(out);
        canonical.root(document, inheritedNamespaces);
        String messageNamespace = document.getName().getNamespaceURI();
        boolean prefixed = !document.getName().getPrefix().isEmpty();
        int depth = 1;
        while (depth > 0) {
            XMLEvent event = events.nextEvent();
            if (event.isStartElement()) {
                depth++;
                QName name = event.asStartElement().getName();
                prefixed |= name.getNamespaceURI().equals(messageNamespace) && !name.getPrefix().isEmpty();
            } else if (event.isEndElement()) {
                depth--;
            }
            canonical.add(event);
        }
        return prefixed ? null : out.toByteArray();
    }

    /**
     * Adds the element's prefixed namespace declarations; the message declares its own default namespace.
     */
    private static void inherit(StartElement element, Map<String, String> namespaces) {
        for (Iterator<Namespace> declared = element.getNamespaces(); declared.hasNext(); ) {
            Namespace namespace = declared.next();
            if (!namespace.isDefaultNamespaceDeclaration()) {
                namespaces.put(namespace.getPrefix(), namespace.getNamespaceURI());
            }
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static boolean isUtf8(StartDocument start) {
        return !start.encodingSet() || start.getCharacterEncodingScheme().equalsIgnoreCase("UTF-8");
    }

    private static ContainerReading fault(String path, String message) {
        return new ContainerReading.Fault(List.of(new Violation(INVALID_FILE_FORMAT, path, message)));
    }
}
