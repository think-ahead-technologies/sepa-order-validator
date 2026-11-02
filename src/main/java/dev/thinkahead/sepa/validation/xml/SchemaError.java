// ABOUTME: A single XSD validation error with its position in the payment file.
// ABOUTME: Line and column are 0 when the parser could not determine a position.
package dev.thinkahead.sepa.validation.xml;

public record SchemaError(int line, int column, String message) {}
