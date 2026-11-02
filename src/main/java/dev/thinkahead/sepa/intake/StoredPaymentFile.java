// ABOUTME: Identification of a stored payment file: id, channel it arrived on and original file name.
// ABOUTME: Used to route status reports back to the right channel.
package dev.thinkahead.sepa.intake;

import java.util.UUID;

record StoredPaymentFile(UUID id, Channel channel, String fileName) {}
