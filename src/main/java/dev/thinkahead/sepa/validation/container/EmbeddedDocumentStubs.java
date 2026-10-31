// ABOUTME: Serves the container schema's imports of the pain schemas as stubs skipping the embedded Document's content.
// ABOUTME: The container check covers the envelope only; every message is schema-validated on its own after the split.
package dev.thinkahead.sepa.validation.container;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Set;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

final class EmbeddedDocumentStubs implements LSResourceResolver {

    private static final String XML_SCHEMA = "http://www.w3.org/2001/XMLSchema";

    // The message namespaces the container schema imports (DFÜ-Abkommen Anlage 3 V3.9, 9.1).
    private static final Set<String> MESSAGE_NAMESPACES = Set.of(
            "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09",
            "urn:iso:std:iso:20022:tech:xsd:pain.007.001.09",
            "urn:iso:std:iso:20022:tech:xsd:pain.008.001.08");

    @Override
    public LSInput resolveResource(String type, String namespaceUri, String publicId, String systemId,
            String baseUri) {
        if (!XML_SCHEMA.equals(type) || !MESSAGE_NAMESPACES.contains(namespaceUri)) {
            return null;
        }
        return new StubInput(systemId, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="%s"
                           elementFormDefault="qualified">
                  <xs:element name="Document">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:any processContents="skip" minOccurs="0" maxOccurs="unbounded"/>
                      </xs:sequence>
                      <xs:anyAttribute processContents="skip"/>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>""".formatted(namespaceUri));
    }

    /**
     * An in-memory schema document; the schema factory only reads its system id and character stream.
     */
    private record StubInput(String systemId, String schema) implements LSInput {

        @Override
        public Reader getCharacterStream() {
            return new StringReader(schema);
        }

        @Override
        public void setCharacterStream(Reader characterStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getByteStream() {
            return null;
        }

        @Override
        public void setByteStream(InputStream byteStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getStringData() {
            return null;
        }

        @Override
        public void setStringData(String stringData) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public void setSystemId(String systemId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getPublicId() {
            return null;
        }

        @Override
        public void setPublicId(String publicId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getBaseURI() {
            return null;
        }

        @Override
        public void setBaseURI(String baseUri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getEncoding() {
            return null;
        }

        @Override
        public void setEncoding(String encoding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getCertifiedText() {
            return false;
        }

        @Override
        public void setCertifiedText(boolean certifiedText) {
            throw new UnsupportedOperationException();
        }
    }
}
