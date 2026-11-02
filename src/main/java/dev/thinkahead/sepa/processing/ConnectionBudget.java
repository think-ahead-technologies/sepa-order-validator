// ABOUTME: Checks at startup that the database connection pool can serve every step worker plus fixed headroom.
// ABOUTME: Without it, a busy backlog makes workers wait for connections and time out instead of processing.
package dev.thinkahead.sepa.processing;

import java.util.Collection;

final class ConnectionBudget {

    /**
     * Connections needed besides the step workers: SFTP intake, leader election heartbeat, overdue-reply gauge and
     * monitor, and the actuator health check.
     */
    static final int HEADROOM = 4;

    private ConnectionBudget() {}

    static void check(int poolSize, Collection<StepSettings> steps) {
        int needed = steps.stream().mapToInt(StepSettings::workers).sum() + HEADROOM;
        if (poolSize < needed) {
            throw new IllegalStateException("spring.datasource.hikari.maximum-pool-size is " + poolSize
                    + " but must be at least " + needed + ": the step workers need "
                    + (needed - HEADROOM) + " connections plus " + HEADROOM + " for intake and operations");
        }
    }
}
