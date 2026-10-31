// ABOUTME: Public facade of the validation module: the recorded validation outcome of a payment file.
// ABOUTME: The REPORT step builds the customer's pain.002 from it.
package dev.thinkahead.sepa.validation;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ValidationOutcomes {

    private final ValidationOutcomeRepository repository;

    ValidationOutcomes(ValidationOutcomeRepository repository) {
        this.repository = repository;
    }

    /**
     * @return the outcome exactly as published in PaymentFileValidated; the file must have been validated
     */
    public PaymentFileValidated find(UUID fileId) {
        return repository.find(fileId);
    }
}
