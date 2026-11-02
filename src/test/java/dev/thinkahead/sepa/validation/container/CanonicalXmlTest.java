// ABOUTME: Unit tests for the streaming C14N 1.0 writer, checked byte for byte against libxml2 (xmllint --c14n).
// ABOUTME: Covers attribute and namespace order, escaping, empty elements, PIs, CDATA, comments and inherited
// namespaces.
package dev.thinkahead.sepa.validation.container;

import static org.assertj.core.api.Assertions.assertThat;

import dev.thinkahead.sepa.validation.xml.SecureXml;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import org.junit.jupiter.api.Test;

class CanonicalXmlTest {

    @Test
    void writesTheSameCanonicalFormAsLibxml2() throws Exception {
        assertThat(canonical(resource("/c14n/sample.xml"), Map.of())).isEqualTo(resource("/c14n/sample.canonical.xml"));
    }

    @Test
    void dropsComments() throws Exception {
        assertThat(canonical("<a><!-- note -->b</a>", Map.of())).isEqualTo("<a>b</a>");
    }

    @Test
    void declaresInheritedNamespacesOnTheRootInPrefixOrder() throws Exception {
        String xml = "<Document xmlns=\"urn:d\"><x:A xmlns:x=\"urn:x\">1</x:A></Document>";

        assertThat(canonical(xml, Map.of("xsi", "http://www.w3.org/2001/XMLSchema-instance", "b", "urn:b")))
                .isEqualTo("<Document xmlns=\"urn:d\" xmlns:b=\"urn:b\" "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><x:A xmlns:x=\"urn:x\">1</x:A>"
                        + "</Document>");
    }

    @Test
    void undeclaresTheDefaultNamespaceOnlyWhereTheParentHadOne() throws Exception {
        assertThat(canonical("<a xmlns=\"urn:a\"><b xmlns=\"\"><c xmlns=\"\"/></b></a>", Map.of()))
                .isEqualTo("<a xmlns=\"urn:a\"><b xmlns=\"\"><c></c></b></a>");
    }

    private static String canonical(String xml, Map<String, String> inheritedNamespaces) throws XMLStreamException {
        XMLEventReader events = SecureXml.inputFactory()
                .createXMLEventReader(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        var out = new ByteArrayOutputStream();
        var canonical = new CanonicalXml(out);
        int depth = 0;
        while (events.hasNext()) {
            XMLEvent event = events.nextEvent();
            if (event.isStartElement()) {
                if (depth == 0) {
                    canonical.root(event.asStartElement(), inheritedNamespaces);
                } else {
                    canonical.add(event);
                }
                depth++;
            } else if (depth > 0) {
                canonical.add(event);
                if (event.isEndElement()) {
                    depth--;
                }
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String resource(String name) {
        try (InputStream in = CanonicalXmlTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
