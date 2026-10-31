// ABOUTME: Implemented by a module for each step it performs; the processing module runs it for every due work item.
// ABOUTME: Work happens outside any transaction; the returned Completion is recorded in one short fenced transaction.
package dev.thinkahead.sepa.processing;

import java.util.UUID;

public interface ProcessingStep {

    Step step();

    /**
     * Does the step's work for the file without holding a transaction, including any external side effect, which
     * must be safe to repeat. An exception makes the attempt fail; it is retried with backoff.
     *
     * @return the database writes that record the step's result
     */
    Completion perform(UUID fileId);
}
