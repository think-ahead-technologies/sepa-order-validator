// ABOUTME: Outcome of validating one payment file's content: identification of the message plus all violations.
// ABOUTME: A file is accepted only when it has no violations at all (whole-file rejection, spec file-validation).
package dev.thinkahead.sepa.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

record ValidationResult(String originalMessageId, String originalMessageNameId, List<Violation> violations) {

    ValidationResult {
        violations = List.copyOf(violations);
    }

    /**
     * Rejection of a file that intake did not store because it exceeds the maximum file size.
     */
    static ValidationResult tooLarge() {
        return new ValidationResult(
                null,
                null,
                List.of(new Violation(
                        PaymentFileValidator.INVALID_FILE_FORMAT,
                        "Document",
                        "File exceeds the maximum size accepted for processing")));
    }

    /**
     * Rejection of a file whose validation failed unexpectedly; the file id lets support find the logged cause.
     */
    static ValidationResult technicalError(UUID fileId) {
        return new ValidationResult(
                null,
                null,
                List.of(new Violation(
                        PaymentFileValidator.INVALID_FILE_FORMAT,
                        "Document",
                        "File could not be processed due to a technical error, reference " + fileId)));
    }

    /**
     * Rejection of a file whose validation did not finish within the deadline for processing.
     */
    static ValidationResult timedOut() {
        return new ValidationResult(
                null,
                null,
                List.of(new Violation(
                        PaymentFileValidator.INVALID_FILE_FORMAT,
                        "Document",
                        "File could not be validated within the time limit for processing")));
    }

    boolean accepted() {
        return violations.isEmpty();
    }

    ValidationResult withViolation(Violation violation) {
        var all = new ArrayList<>(violations);
        all.add(violation);
        return new ValidationResult(originalMessageId, originalMessageNameId, all);
    }
}
