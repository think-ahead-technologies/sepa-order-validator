// ABOUTME: Event published when a pain.002 status report has been stored and delivered to the customer.
// ABOUTME: Part of the reporting module's public API.
package dev.thinkahead.sepa.reporting;

import java.util.UUID;

public record StatusReportDelivered(UUID fileId, String reportMessageId, GroupStatus groupStatus) {
}
