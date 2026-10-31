// ABOUTME: Outcome of validating one payment file's content: identification of the message plus all violations.
// ABOUTME: A file is accepted only when it has no violations at all (whole-file rejection, ADR-0006).
package dev.thinkahead.sepa.validation;

import java.util.ArrayList;
import java.util.List;

record ValidationResult(String originalMessageId, String originalMessageNameId, List<Violation> violations) {

    ValidationResult {
        violations = List.copyOf(violations);
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
