// ABOUTME: Reads DK XML containers (DFÜ-Abkommen Anlage 3 V3.9, 9.1): checks the envelope and splits out the messages.
// ABOUTME: Only credit transfer containers in UTF-8 without namespace prefixes on the messages are accepted; else FF01.
package dev.thinkahead.sepa.validation.container;

import dev.thinkahead.sepa.validation.Violation;
import dev.thinkahead.sepa.validation.xml.SchemaError;
import dev.thinkahead.sepa.validation.xml.SchemaValidator;
import dev.thinkahead.sepa.validation.xml.SecureXml;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
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
    private static final String INVALID_FILE_FORMAT = "FF01";
    private static final int MESSAGE_DEPTH = 2;
    private static final int DOCUMENT_DEPTH = 3;

    private final Schema schema;
    private final SchemaValidator schemaValidator;
    private final XMLOutputFactory outputFactory = XMLOutputFactory.newDefaultFactory();
    private final XMLEventFactory eventFactory = XMLEventFactory.newDefaultFactory();

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
            return fault("Container is not readable");
        }
    }

    private ContainerReading split(InputStream content) throws XMLStreamException {
        XMLEventReader events = SecureXml.inputFactory().createXMLEventReader(content);
        try {
            List<byte[]> messages = new ArrayList<>();
            Map<String, String> inheritedNamespaces = new HashMap<>();
            int depth = 0;
            while (events.hasNext()) {
                XMLEvent event = events.nextEvent();
                if (event.isStartDocument() && !isUtf8((StartDocument) event)) {
                    return fault("Container must be encoded in UTF-8");
                }
                if (event.isStartElement()) {
                    depth++;
                    StartElement element = event.asStartElement();
                    String name = element.getName().getLocalPart();
                    boolean messageElement = depth == MESSAGE_DEPTH && name.startsWith(MESSAGE_PREFIX);
                    if (messageElement && !name.equals(CREDIT_TRANSFER_MESSAGE)) {
                        return fault("Only credit transfer containers (" + CREDIT_TRANSFER_MESSAGE + ") are supported");
                    }
                    if (depth == DOCUMENT_DEPTH && name.equals(DOCUMENT)) {
                        byte[] message = copyMessage(element, events, inheritedNamespaces);
                        if (message == null) {
                            return fault("Embedded messages must not use a namespace prefix");
                        }
                        messages.add(message);
                        depth--;
                    } else if (depth < DOCUMENT_DEPTH) {
                        inherit(element, inheritedNamespaces);
                    }
                } else if (event.isEndElement()) {
                    depth--;
                }
            }
            return new ContainerReading.Split(messages);
        } finally {
            events.close();
        }
    }

    /**
     * Writes the message from its Document start element up to the matching end element as a standalone document.
     *
     * @return the message, or null if an element of the message uses a namespace prefix
     */
    private byte[] copyMessage(StartElement document, XMLEventReader events, Map<String, String> inheritedNamespaces)
            throws XMLStreamException {
        var out = new ByteArrayOutputStream();
        XMLEventWriter writer = outputFactory.createXMLEventWriter(out, StandardCharsets.UTF_8.name());
        writer.add(eventFactory.createStartDocument(StandardCharsets.UTF_8.name(), "1.0"));
        writer.add(withInheritedNamespaces(document, inheritedNamespaces));
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
            writer.add(event);
        }
        writer.add(eventFactory.createEndDocument());
        writer.close();
        return prefixed ? null : out.toByteArray();
    }

    /**
     * The Document start element plus the prefixed namespace declarations of the container that it does not redeclare,
     * so prefixes used inside the message (e.g. xsi) stay bound once it stands alone.
     */
    private StartElement withInheritedNamespaces(StartElement document, Map<String, String> inheritedNamespaces) {
        Map<String, String> namespaces = new HashMap<>(inheritedNamespaces);
        for (Iterator<Namespace> own = document.getNamespaces(); own.hasNext(); ) {
            Namespace namespace = own.next();
            namespaces.put(namespace.getPrefix(), namespace.getNamespaceURI());
        }
        List<Namespace> declarations = namespaces.entrySet().stream()
                .map(entry -> entry.getKey().isEmpty()
                        ? eventFactory.createNamespace(entry.getValue())
                        : eventFactory.createNamespace(entry.getKey(), entry.getValue()))
                .toList();
        return eventFactory.createStartElement(document.getName(), document.getAttributes(), declarations.iterator());
    }

    private static void inherit(StartElement element, Map<String, String> inheritedNamespaces) {
        for (Iterator<Namespace> declared = element.getNamespaces(); declared.hasNext(); ) {
            Namespace namespace = declared.next();
            if (!namespace.isDefaultNamespaceDeclaration()) {
                inheritedNamespaces.put(namespace.getPrefix(), namespace.getNamespaceURI());
            }
        }
    }

    private static boolean isUtf8(StartDocument start) {
        return !start.encodingSet() || start.getCharacterEncodingScheme().equalsIgnoreCase("UTF-8");
    }

    private static ContainerReading fault(String message) {
        return new ContainerReading.Fault(List.of(new Violation(INVALID_FILE_FORMAT, "Container", message)));
    }
}
