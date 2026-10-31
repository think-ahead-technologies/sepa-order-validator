// ABOUTME: Leader election timing (prefix "sepa.leader-election"): lock time-to-live and heartbeat interval.
// ABOUTME: The time-to-live bounds failover after a crash; the heartbeat must renew the lock well before it expires.
package dev.thinkahead.sepa.intake;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sepa.leader-election")
record LeaderElectionProperties(Duration lockTimeToLive, Duration heartbeat) {

    LeaderElectionProperties {
        if (lockTimeToLive == null || heartbeat == null) {
            throw new IllegalArgumentException("sepa.leader-election.lock-time-to-live and heartbeat are required");
        }
        if (!lockTimeToLive.isPositive()) {
            throw new IllegalArgumentException(
                    "sepa.leader-election.lock-time-to-live must be positive, was " + lockTimeToLive);
        }
        if (!heartbeat.isPositive()) {
            throw new IllegalArgumentException("sepa.leader-election.heartbeat must be positive, was " + heartbeat);
        }
        if (heartbeat.multipliedBy(2).compareTo(lockTimeToLive) > 0) {
            throw new IllegalArgumentException("sepa.leader-election.heartbeat " + heartbeat
                    + " must be at most half of lock-time-to-live " + lockTimeToLive);
        }
    }
}
