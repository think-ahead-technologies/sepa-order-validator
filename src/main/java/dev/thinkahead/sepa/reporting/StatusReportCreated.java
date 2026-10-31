// ABOUTME: Event published once a payment file's pain.002 status report has been generated and stored.
// ABOUTME: Triggers delivery of the stored report; delivering it again re-sends identical content.
package dev.thinkahead.sepa.reporting;

import java.util.UUID;

public record StatusReportCreated(UUID fileId, String reportMessageId) {
}
