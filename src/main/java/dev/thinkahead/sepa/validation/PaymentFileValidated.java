// ABOUTME: Event published when a received payment file has been validated, accepted or rejected as a whole.
// ABOUTME: Original message id and name are null when the file could not be identified as a supported message.
package dev.thinkahead.sepa.validation;

import java.util.List;
import java.util.UUID;

public record PaymentFileValidated(
        UUID fileId,
        String originalMessageId,
        String originalMessageNameId,
        boolean accepted,
        List<Violation> violations) {
}
