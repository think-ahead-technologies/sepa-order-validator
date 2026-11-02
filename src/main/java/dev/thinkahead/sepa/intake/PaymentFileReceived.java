// ABOUTME: Event published when a payment file has been received from a channel and stored.
// ABOUTME: The submitter is the authenticated channel identity (e.g. "sftp:<account>"), never taken from the file.
package dev.thinkahead.sepa.intake;

import java.util.UUID;

public record PaymentFileReceived(UUID fileId, Channel channel, String submitter, String fileName, String sha256) {}
