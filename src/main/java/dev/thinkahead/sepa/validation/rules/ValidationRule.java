// ABOUTME: A single, independent validation rule applied to one part of a payment file.
// ABOUTME: Returns all violations it finds; an empty list means the subject satisfies the rule.
package dev.thinkahead.sepa.validation.rules;

import dev.thinkahead.sepa.validation.Violation;
import java.util.List;

@FunctionalInterface
public interface ValidationRule<T> {

    List<Violation> validate(T subject);
}
