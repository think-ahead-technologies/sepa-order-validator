// ABOUTME: Worker settings of one processing step: pool size, lease, timeout, attempts, polling and retry backoff.
// ABOUTME: Validated at startup (the timeout must end before the lease); backoff doubles per failed attempt.
package dev.thinkahead.sepa.processing;

import java.time.Duration;

/**
 * @param workers        items of this step processed at once per instance
 * @param lease          how long a claimed item stays with its worker
 * @param timeout        how long one attempt may work; below the lease, so no second worker starts meanwhile
 * @param maxAttempts    attempts before the item fails for good and needs a manual re-drive
 * @param pollInterval   pause between looking for due items while none are found
 * @param initialBackoff delay before retrying after the first failed attempt
 * @param maxBackoff     upper bound of the retry delay
 */
record StepSettings(
        int workers,
        Duration lease,
        Duration timeout,
        int maxAttempts,
        Duration pollInterval,
        Duration initialBackoff,
        Duration maxBackoff) {

    StepSettings {
        if (workers < 1) {
            throw new IllegalArgumentException("workers must be at least 1, was " + workers);
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("max-attempts must be at least 1, was " + maxAttempts);
        }
        requirePositive("lease", lease);
        requirePositive("timeout", timeout);
        if (timeout.compareTo(lease) >= 0) {
            throw new IllegalArgumentException("timeout " + timeout + " must be below the lease " + lease);
        }
        requirePositive("poll-interval", pollInterval);
        requirePositive("initial-backoff", initialBackoff);
        requirePositive("max-backoff", maxBackoff);
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException(
                    "max-backoff " + maxBackoff + " must not be below initial-backoff " + initialBackoff);
        }
    }

    /**
     * When the worker interrupts an attempt still running: halfway between its timeout and the end of its lease, so
     * a step that honours its deadline is never interrupted and no second worker can start meanwhile.
     */
    Duration hardStopAfter() {
        return timeout.plus(lease.minus(timeout).dividedBy(2));
    }

    /**
     * @param failedAttempt number of the attempt that just failed, starting at 1
     */
    Duration backoffAfter(int failedAttempt) {
        int doublings = Math.min(failedAttempt - 1, 30);
        Duration backoff = initialBackoff.multipliedBy(1L << doublings);
        return backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff;
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || !value.isPositive()) {
            throw new IllegalArgumentException(name + " must be positive, was " + value);
        }
    }
}
