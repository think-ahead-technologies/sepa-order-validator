// ABOUTME: Validates a payment file's content: format detection, hardened XSD validation, then content rules.
// ABOUTME: Content rules run only on schema-valid files; all rule violations are collected, not just the first.
package dev.thinkahead.sepa.validation;

import dev.thinkahead.sepa.validation.pain001.GroupHeader;
import dev.thinkahead.sepa.validation.pain001.Pain001Parser;
import dev.thinkahead.sepa.validation.rules.SepaCreditTransferRules;
import dev.thinkahead.sepa.validation.xml.FormatDetector;
import dev.thinkahead.sepa.validation.xml.MessageFormat;
import dev.thinkahead.sepa.validation.xml.SchemaError;
import dev.thinkahead.sepa.validation.xml.SchemaValidator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class PaymentFileValidator {

    static final String INVALID_FILE_FORMAT = "FF01";

    private final FormatDetector formatDetector = new FormatDetector();
    private final SchemaValidator schemaValidator = new SchemaValidator();
    private final Pain001Parser parser = new Pain001Parser();

    ValidationResult validate(byte[] content) {
        Optional<MessageFormat> detected = formatDetector.detect(content);
        if (detected.isEmpty()) {
            return new ValidationResult(null, null, List.of(new Violation(INVALID_FILE_FORMAT, "Document",
                    "Unsupported or unreadable message format")));
        }
        MessageFormat format = detected.get();

        List<SchemaError> schemaErrors = schemaValidator.validate(format, content);
        if (!schemaErrors.isEmpty()) {
            return new ValidationResult(null, format.messageNameId(), schemaErrors.stream()
                    .map(error -> new Violation(INVALID_FILE_FORMAT,
                            "Line " + error.line() + ", column " + error.column(), error.message()))
                    .toList());
        }

        var rules = new SepaCreditTransferRules();
        GroupHeader header = parser.parse(content, rules);
        return new ValidationResult(header.messageId(), format.messageNameId(), rules.violations());
    }
}
