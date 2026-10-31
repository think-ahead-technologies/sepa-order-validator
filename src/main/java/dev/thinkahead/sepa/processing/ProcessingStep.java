// ABOUTME: Implemented by a module for each step it performs; the processing module runs it for every due work item.
// ABOUTME: Work happens outside any transaction; the returned Completion is recorded in one short fenced transaction.
package dev.thinkahead.sepa.processing;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProcessingStep {

    Step step();

    /**
     * Does the step's work for the file without holding a transaction, including any external side effect, which
     * must be safe to repeat. An exception makes the attempt fail; it is retried with backoff.
     *
     * @param deadline when the work must be finished; it ends before the lease, so no other worker starts meanwhile
     * @return the database writes that record the step's result
     */
    Completion perform(UUID fileId, Instant deadline);

    /**
     * The outcome to record once every attempt has failed, without loading the file again: the file may be what
     * crashed the earlier attempts. Without one, the work item fails and needs a manual re-drive.
     */
    default Optional<Completion> whenAttemptsExhausted(UUID fileId) {
        return Optional.empty();
    }
}
