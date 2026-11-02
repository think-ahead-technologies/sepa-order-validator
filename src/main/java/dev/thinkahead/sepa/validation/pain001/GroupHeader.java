// ABOUTME: Identification data from the group header (GrpHdr) of a pain.001 message.
// ABOUTME: Used to reference the original message in the pain.002 status report.
package dev.thinkahead.sepa.validation.pain001;

public record GroupHeader(String messageId) {}
