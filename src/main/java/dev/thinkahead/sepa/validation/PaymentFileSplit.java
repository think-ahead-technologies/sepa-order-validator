// ABOUTME: Event published when a DK XML container has been split into its messages, each now a payment file.
// ABOUTME: The container is answered through its messages' status reports, not with a report of its own (ADR-0020).
package dev.thinkahead.sepa.validation;

import java.util.UUID;

public record PaymentFileSplit(UUID fileId, int messages) {
}
