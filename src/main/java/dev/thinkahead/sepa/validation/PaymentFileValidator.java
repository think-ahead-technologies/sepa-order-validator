// ABOUTME: Validates a payment file's content: format detection, hardened XSD validation, then content rules.
// ABOUTME: Rules run only on schema-valid files and collect all violations; a deadline bounds the whole validation.
package dev.thinkahead.sepa.validation;

import dev.thinkahead.sepa.validation.pain001.GroupHeader;
import dev.thinkahead.sepa.validation.pain001.Pain001Parser;
import dev.thinkahead.sepa.validation.rules.SepaCreditTransferRules;
import dev.thinkahead.sepa.validation.xml.FormatDetector;
import dev.thinkahead.sepa.validation.xml.MessageFormat;
import dev.thinkahead.sepa.validation.xml.SchemaError;
import dev.thinkahead.sepa.validation.xml.SchemaValidator;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
class PaymentFileValidator {

    static final String INVALID_FILE_FORMAT = "FF01";

    /** Upper bound of violations listed per file, keeping pain.002 reports and memory use bounded. */
    static final int MAX_REPORTED_VIOLATIONS = 1_000;

    private final FormatDetector formatDetector = new FormatDetector();
    private final SchemaValidator schemaValidator = new SchemaValidator(MAX_REPORTED_VIOLATIONS);
    private final Pain001Parser parser = new Pain001Parser();

    /**
     * @param deadline once passed, validation stops and the file is rejected with FF01 (time limit)
     */
    ValidationResult validate(byte[] content, Instant deadline) {
        try {
            return validateBefore(content, deadline);
        } catch (ValidationDeadlineExceeded e) {
            return ValidationResult.timedOut();
        }
    }

    private ValidationResult validateBefore(byte[] content, Instant deadline) {
        Optional<MessageFormat> detected = formatDetector.detect(content);
        if (detected.isEmpty()) {
            return new ValidationResult(null, null, List.of(new Violation(INVALID_FILE_FORMAT, "Document",
                    "Unsupported or unreadable message format")));
        }
        MessageFormat format = detected.get();

        List<SchemaError> schemaErrors = beforeDeadline(deadline,
                () -> schemaValidator.validate(format, read(content, deadline)));
        if (!schemaErrors.isEmpty()) {
            return new ValidationResult(null, format.messageNameId(), schemaErrors.stream()
                    .map(error -> new Violation(INVALID_FILE_FORMAT,
                            "Line " + error.line() + ", column " + error.column(), error.message()))
                    .toList());
        }

        var rules = new SepaCreditTransferRules(MAX_REPORTED_VIOLATIONS);
        GroupHeader header = beforeDeadline(deadline, () -> parser.parse(read(content, deadline), rules));
        return new ValidationResult(header.messageId(), format.messageNameId(), rules.violations());
    }

    private static InputStream read(byte[] content, Instant deadline) {
        return new DeadlineInputStream(new ByteArrayInputStream(content), deadline);
    }

    /**
     * XML libraries may report the stopped stream as their own error; once the deadline has passed, any outcome of
     * the phase counts as running out of time.
     */
    private static <T> T beforeDeadline(Instant deadline, Supplier<T> phase) {
        try {
            T result = phase.get();
            if (Instant.now().isAfter(deadline)) {
                throw new ValidationDeadlineExceeded();
            }
            return result;
        } catch (RuntimeException e) {
            if (Instant.now().isAfter(deadline)) {
                throw new ValidationDeadlineExceeded();
            }
            throw e;
        }
    }
}
