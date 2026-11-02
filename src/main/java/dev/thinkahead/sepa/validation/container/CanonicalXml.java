// ABOUTME: Streaming writer for Canonical XML 1.0 without comments (W3C REC-xml-c14n-20010315) of one element subtree.
// ABOUTME: Namespaces in scope from outside the subtree are passed in, as when canonicalising it in its document.
package dev.thinkahead.sepa.validation.container;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.namespace.QName;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.ProcessingInstruction;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

final class CanonicalXml {

    private static final Comparator<Attribute> ATTRIBUTE_ORDER = Comparator.comparing(
                    (Attribute attribute) -> attribute.getName().getNamespaceURI())
            .thenComparing(attribute -> attribute.getName().getLocalPart());

    private final OutputStream out;
    // Namespace bindings in scope for each open element, prefix "" for the default namespace.
    private final Deque<Map<String, String>> scopes = new ArrayDeque<>();

    CanonicalXml(OutputStream out) {
        this.out = out;
    }

    /**
     * Writes the subtree's root, declaring every namespace in scope: those passed in plus its own declarations.
     */
    void root(StartElement element, Map<String, String> inheritedNamespaces) {
        Map<String, String> scope = new HashMap<>(inheritedNamespaces);
        declared(element).forEach(scope::put);
        Map<String, String> rendered = new TreeMap<>(scope);
        if (rendered.getOrDefault("", "").isEmpty()) {
            rendered.remove("");
        }
        start(element, scope, rendered);
    }

    /**
     * Writes an event inside the subtree; comments are left out.
     */
    void add(XMLEvent event) {
        switch (event.getEventType()) {
            case XMLEvent.START_ELEMENT -> child(event.asStartElement());
            case XMLEvent.END_ELEMENT -> {
                write("</" + qualified(event.asEndElement().getName()) + ">");
                scopes.pop();
            }
            case XMLEvent.CHARACTERS, XMLEvent.CDATA, XMLEvent.SPACE ->
                write(escapeText(event.asCharacters().getData()));
            case XMLEvent.PROCESSING_INSTRUCTION -> {
                var instruction = (ProcessingInstruction) event;
                String data = instruction.getData() == null
                        ? ""
                        : instruction.getData().strip();
                write("<?" + instruction.getTarget() + (data.isEmpty() ? "" : " " + data) + "?>");
            }
            default -> {}
        }
    }

    private void child(StartElement element) {
        Map<String, String> parent = scopes.peek();
        Map<String, String> scope = new HashMap<>(parent);
        Map<String, String> rendered = new TreeMap<>();
        declared(element).forEach((prefix, uri) -> {
            String inherited = parent.getOrDefault(prefix, "");
            if (!uri.equals(inherited)) {
                rendered.put(prefix, uri);
            }
            scope.put(prefix, uri);
        });
        start(element, scope, rendered);
    }

    private void start(StartElement element, Map<String, String> scope, Map<String, String> renderedNamespaces) {
        scopes.push(scope);
        var tag = new StringBuilder("<").append(qualified(element.getName()));
        renderedNamespaces.forEach((prefix, uri) -> tag.append(prefix.isEmpty() ? " xmlns" : " xmlns:" + prefix)
                .append("=\"")
                .append(escapeAttribute(uri))
                .append('"'));
        List<Attribute> attributes = new ArrayList<>();
        element.getAttributes().forEachRemaining(attributes::add);
        attributes.sort(ATTRIBUTE_ORDER);
        for (Attribute attribute : attributes) {
            tag.append(' ')
                    .append(qualified(attribute.getName()))
                    .append("=\"")
                    .append(escapeAttribute(attribute.getValue()))
                    .append('"');
        }
        write(tag.append('>').toString());
    }

    private static Map<String, String> declared(StartElement element) {
        Map<String, String> declared = new HashMap<>();
        for (Iterator<Namespace> namespaces = element.getNamespaces(); namespaces.hasNext(); ) {
            Namespace namespace = namespaces.next();
            // StAX reports the undeclaration xmlns="" with a null namespace URI.
            declared.put(namespace.getPrefix(), namespace.getNamespaceURI() == null ? "" : namespace.getNamespaceURI());
        }
        return declared;
    }

    private static String qualified(QName name) {
        return name.getPrefix().isEmpty() ? name.getLocalPart() : name.getPrefix() + ":" + name.getLocalPart();
    }

    private static String escapeText(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\r", "&#xD;");
    }

    private static String escapeAttribute(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace("\"", "&quot;")
                .replace("\t", "&#x9;")
                .replace("\n", "&#xA;")
                .replace("\r", "&#xD;");
    }

    private void write(String text) {
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
