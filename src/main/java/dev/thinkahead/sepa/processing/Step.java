// ABOUTME: Processing steps of a payment file, run as work items on the work queue (spec file-processing).
// ABOUTME: Each step's completion schedules the next: VALIDATE, then REPORT, then DELIVER.
package dev.thinkahead.sepa.processing;

public enum Step {
    VALIDATE,
    REPORT,
    DELIVER
}
