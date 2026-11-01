// ABOUTME: The only place that creates XML parsers and schema validators, all hardened against XXE and DTD attacks.
// ABOUTME: Disables DTDs and external entities and blocks external DTD/schema access (spec xml-security).
package dev.thinkahead.sepa.validation.xml;

import java.net.URL;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

public final class SecureXml {

    private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";

    private SecureXml() {
    }

    public static XMLInputFactory inputFactory() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        return factory;
    }

    public static XMLReader xmlReader() throws SAXException {
        try {
            SAXParserFactory factory = SAXParserFactory.newDefaultInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature(DISALLOW_DOCTYPE, true);
            return factory.newSAXParser().getXMLReader();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Cannot create hardened SAX parser", e);
        }
    }

    public static Schema schema(URL xsd) throws SAXException {
        return schema(xsd, null);
    }

    /**
     * Compiles a schema whose imports are served by the resolver, since external schema access stays blocked.
     */
    public static Schema schema(URL xsd, LSResourceResolver imports) throws SAXException {
        SchemaFactory factory = SchemaFactory.newDefaultInstance();
        factory.setResourceResolver(imports);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newSchema(xsd);
    }

    public static Validator validator(Schema schema) throws SAXException {
        Validator validator = schema.newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return validator;
    }
}
