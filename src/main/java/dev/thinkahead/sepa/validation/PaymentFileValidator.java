// ABOUTME: Validates a payment file: format detection, hardened ISO XSD, content rules, then the DK subset XSD.
// ABOUTME: Rules run only on schema-valid files; DK containers are read and split instead. A deadline bounds both.
package dev.thinkahead.sepa.validation;

import dev.thinkahead.sepa.intake.Submission;
import dev.thinkahead.sepa.validation.container.ContainerReader;
import dev.thinkahead.sepa.validation.container.ContainerReading;
import dev.thinkahead.sepa.validation.pain001.GroupHeader;
import dev.thinkahead.sepa.validation.pain001.Pain001Parser;
import dev.thinkahead.sepa.validation.rules.SepaCreditTransferRules;
import dev.thinkahead.sepa.validation.xml.FormatDetector;
import dev.thinkahead.sepa.validation.xml.MessageFormat;
import dev.thinkahead.sepa.validation.xml.SchemaError;
import dev.thinkahead.sepa.validation.xml.SchemaValidator;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
class PaymentFileValidator {

    static final String INVALID_FILE_FORMAT = "FF01";

    /** Marks schema errors of the DK subset, so the customer can tell them from errors against the ISO schema. */
    static final String DK_SUBSET = "DK subset GBIC_5: ";

    /** The bank's time zone: it decides which calendar day "today" is for execution dates. */
    static final ZoneId BANK_ZONE = ZoneId.of("Europe/Berlin");

    /** Upper bound of violations listed per file, keeping pain.002 reports and memory use bounded. */
    static final int MAX_REPORTED_VIOLATIONS = 1_000;

    private final FormatDetector formatDetector = new FormatDetector();
    private final SchemaValidator schemaValidator = new SchemaValidator(MAX_REPORTED_VIOLATIONS);
    private final Pain001Parser parser = new Pain001Parser();
    private final ContainerReader containerReader = new ContainerReader(MAX_REPORTED_VIOLATIONS);
    private final Clock clock;

    PaymentFileValidator(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param deadline once passed, validation stops and the file is rejected with FF01 (time limit)
     */
    ValidationResult validate(byte[] content, Instant deadline) {
        return validate(content, deadline, Submission.REGULAR);
    }

    /**
     * @param submission how the file was submitted; its content must match it
     */
    ValidationResult validate(byte[] content, Instant deadline, Submission submission) {
        try {
            return validateBefore(content, deadline, submission);
        } catch (ValidationDeadlineExceeded e) {
            return ValidationResult.timedOut();
        }
    }

    /**
     * Reads the file as a DK XML container if it is one.
     *
     * @return empty if the file is not a container
     */
    Optional<ContainerReading> readContainer(byte[] content, Instant deadline) {
        if (!containerReader.isContainer(content)) {
            return Optional.empty();
        }
        try {
            return Optional.of(beforeDeadline(deadline, () -> containerReader.read(() -> read(content, deadline))));
        } catch (ValidationDeadlineExceeded e) {
            return Optional.of(new ContainerReading.Fault(ValidationResult.timedOut().violations()));
        }
    }

    private ValidationResult validateBefore(byte[] content, Instant deadline, Submission submission) {
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

        var rules = new SepaCreditTransferRules(MAX_REPORTED_VIOLATIONS, LocalDate.now(clock.withZone(BANK_ZONE)),
                submission);
        GroupHeader header = beforeDeadline(deadline, () -> parser.parse(read(content, deadline), rules));
        if (!rules.violations().isEmpty()) {
            return new ValidationResult(header.messageId(), format.messageNameId(), rules.violations());
        }

        // Last gate (ADR-0022): a file the rules pass is accepted only if it also meets the DK subset.
        List<SchemaError> dkSubsetErrors = beforeDeadline(deadline,
                () -> schemaValidator.validateDkSubset(format, read(content, deadline)));
        return new ValidationResult(header.messageId(), format.messageNameId(), dkSubsetErrors.stream()
                .map(error -> new Violation(INVALID_FILE_FORMAT,
                        "Line " + error.line() + ", column " + error.column(), DK_SUBSET + error.message()))
                .toList());
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
