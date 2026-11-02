// ABOUTME: A single validation finding in a payment file, part of the validation module's public API.
// ABOUTME: Carries an ISO 20022 external status reason code, the location in the file and a readable message.
package dev.thinkahead.sepa.validation;

public record Violation(String reasonCode, String path, String message) {}
