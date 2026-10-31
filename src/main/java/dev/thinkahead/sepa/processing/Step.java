// ABOUTME: Processing steps of a payment file that run as work items on the PostgreSQL work queue (ADR-0017).
// ABOUTME: Steps move here one by one from the event chain; REPORT still runs on module events.
package dev.thinkahead.sepa.processing;

public enum Step {
    VALIDATE,
    DELIVER
}
