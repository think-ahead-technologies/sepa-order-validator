// ABOUTME: Supported payment message formats, identified by the XML namespace of the document root.
// ABOUTME: Each format knows its ISO message name, its ISO XSD and the XSD of the DK technical validation subset.
package dev.thinkahead.sepa.validation.xml;

import java.util.Arrays;
import java.util.Optional;

public enum MessageFormat {

    /**
     * The DK subset is the technical validation subset GBIC_5 of DFÜ-Abkommen Anlage 3 V3.9 (ebics.de, file
     * DK-TVS_SEPA_GBIC_5zzglISO_Originale.zip), unchanged; it restricts the message to SEPA, e.g. payment method TRF
     * and charge bearer SLEV only (spec file-validation).
     */
    PAIN_001_001_09("urn:iso:std:iso:20022:tech:xsd:pain.001.001.09", "pain.001.001.09",
            "/xsd/pain.001.001.09.xsd", "/xsd/pain.001.001.09_GBIC_5.xsd");

    private final String namespace;
    private final String messageNameId;
    private final String schemaResource;
    private final String dkSubsetSchemaResource;

    MessageFormat(String namespace, String messageNameId, String schemaResource, String dkSubsetSchemaResource) {
        this.namespace = namespace;
        this.messageNameId = messageNameId;
        this.schemaResource = schemaResource;
        this.dkSubsetSchemaResource = dkSubsetSchemaResource;
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

    String dkSubsetSchemaResource() {
        return dkSubsetSchemaResource;
    }
}
