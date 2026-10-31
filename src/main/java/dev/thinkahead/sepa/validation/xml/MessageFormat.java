// ABOUTME: Supported payment message formats, identified by the XML namespace of the document root.
// ABOUTME: Each format knows its ISO message name and the bundled XSD it is validated against.
package dev.thinkahead.sepa.validation.xml;

import java.util.Arrays;
import java.util.Optional;

public enum MessageFormat {

    PAIN_001_001_09("urn:iso:std:iso:20022:tech:xsd:pain.001.001.09", "pain.001.001.09", "/xsd/pain.001.001.09.xsd");

    private final String namespace;
    private final String messageNameId;
    private final String schemaResource;

    MessageFormat(String namespace, String messageNameId, String schemaResource) {
        this.namespace = namespace;
        this.messageNameId = messageNameId;
        this.schemaResource = schemaResource;
    }

    public static Optional<MessageFormat> byNamespace(String namespace) {
        return Arrays.stream(values()).filter(format -> format.namespace.equals(namespace)).findFirst();
    }

    public String messageNameId() {
        return messageNameId;
    }

    String schemaResource() {
        return schemaResource;
    }
}
