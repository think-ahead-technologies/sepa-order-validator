// ABOUTME: Validates payment files against the bundled XSD of their message format using a hardened parser.
// ABOUTME: Collects schema errors up to a cap and only logs warnings; compiled schemas are cached per format.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class SchemaValidator {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaValidator.class);

    private final Map<MessageFormat, Schema> schemas = new ConcurrentHashMap<>();
    private final int maxErrors;

    /**
     * @param maxErrors validation stops once this many errors are collected, keeping memory use bounded
     */
    public SchemaValidator(int maxErrors) {
        this.maxErrors = maxErrors;
    }

    public List<SchemaError> validate(MessageFormat format, byte[] content) {
        List<SchemaError> errors = new ArrayList<>();
        try {
            Validator validator = SecureXml.validator(schemaFor(format));
            validator.setErrorHandler(new CollectingErrorHandler(errors, maxErrors));
            validator.validate(new SAXSource(SecureXml.xmlReader(), new InputSource(new ByteArrayInputStream(content))));
        } catch (ErrorCapReached e) {
            errors.add(new SchemaError(0, 0, "Schema validation stopped after " + maxErrors + " errors"));
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

    private static final class ErrorCapReached extends SAXException {
    }

    /**
     * Collects errors as schema violations. Warnings do not make a file schema-invalid, so they are only logged.
     */
    record CollectingErrorHandler(List<SchemaError> errors, int maxErrors) implements ErrorHandler {

        @Override
        public void warning(SAXParseException e) {
            // Position only: parser messages can quote values from the payment file (ADR-0018).
            LOG.info("Schema validation warning at line {}, column {}", e.getLineNumber(), e.getColumnNumber());
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            collect(e);
        }

        private void collect(SAXParseException e) throws SAXException {
            if (errors.size() >= maxErrors) {
                throw new ErrorCapReached();
            }
            errors.add(toError(e));
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    }
}
