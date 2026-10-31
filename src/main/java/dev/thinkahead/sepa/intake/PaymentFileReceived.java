// ABOUTME: Event published when a payment file has been received from a channel and stored.
// ABOUTME: Carries identification only; the content is fetched via PaymentFiles to keep events small.
package dev.thinkahead.sepa.intake;

import java.util.UUID;

public record PaymentFileReceived(UUID fileId, Channel channel, String fileName, String sha256) {
}
