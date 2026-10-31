// ABOUTME: Thrown when a step attempt has not finished by its hard stop, shortly before its lease would expire.
// ABOUTME: The attempt is interrupted and counts as failed, so it is retried with backoff.
package dev.thinkahead.sepa.processing;

import java.time.Duration;

class StepTimedOut extends RuntimeException {

    StepTimedOut(Step step, Duration timeout) {
        super(step + " did not finish within " + timeout + " and was interrupted");
    }
}
