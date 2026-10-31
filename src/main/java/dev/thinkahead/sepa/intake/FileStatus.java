// ABOUTME: Lifecycle states of a received payment file (ADR-0007); SPLIT ends a container split into messages.
// ABOUTME: Screening and manual review states join when the screening module is built.
package dev.thinkahead.sepa.intake;

enum FileStatus {
    RECEIVED,
    ACCEPTED,
    REJECTED,
    SPLIT
}
