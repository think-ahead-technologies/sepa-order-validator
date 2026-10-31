// ABOUTME: Validates payment files against the bundled XSD of their message format using a hardened parser.
// ABOUTME: Collects all schema errors; compiled schemas are cached per format because they are thread-safe.
package dev.thinkahead.sepa.validation.xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class SchemaValidator {

    private final Map<MessageFormat, Schema> schemas = new ConcurrentHashMap<>();

    public List<SchemaError> validate(MessageFormat format, byte[] content) {
        List<SchemaError> errors = new ArrayList<>();
        try {
            Validator validator = SecureXml.validator(schemaFor(format));
            validator.setErrorHandler(new CollectingErrorHandler(errors));
            validator.validate(new SAXSource(SecureXml.xmlReader(), new InputSource(new ByteArrayInputStream(content))));
        } catch (SAXParseException e) {
            errors.add(toError(e));
        } catch (SAXException | IOException e) {
            errors.add(new SchemaError(0, 0, e.getMessage()));
        }
        return errors;
    }

    private Schema schemaFor(MessageFormat format) {
        return schemas.computeIfAbsent(format, f -> {
            URL xsd = SchemaValidator.class.getResource(f.schemaResource());
            if (xsd == null) {
                throw new IllegalStateException("Missing XSD " + f.schemaResource());
            }
            try {
                return SecureXml.schema(xsd);
            } catch (SAXException e) {
                throw new IllegalStateException("Cannot compile XSD " + f.schemaResource(), e);
            }
        });
    }

    private static SchemaError toError(SAXParseException e) {
        return new SchemaError(e.getLineNumber(), e.getColumnNumber(), e.getMessage());
    }

    private record CollectingErrorHandler(List<SchemaError> errors) implements ErrorHandler {

        @Override
        public void warning(SAXParseException e) {
            errors.add(toError(e));
        }

        @Override
        public void error(SAXParseException e) {
            errors.add(toError(e));
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    }
}
