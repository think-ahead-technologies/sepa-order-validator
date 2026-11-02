// ABOUTME: Unit tests for LeaderElectionProperties validation of lock time-to-live and heartbeat.
// ABOUTME: Both must be positive, and the heartbeat must renew the lock well before it expires.
package dev.thinkahead.sepa.intake;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LeaderElectionPropertiesTest {

    @Test
    void acceptsAHeartbeatOfAtMostHalfTheTimeToLive() {
        assertThatNoException()
                .isThrownBy(() -> new LeaderElectionProperties(Duration.ofSeconds(10), Duration.ofSeconds(5)));
    }

    @Test
    void requiresBothDurations() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LeaderElectionProperties(null, Duration.ofSeconds(1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LeaderElectionProperties(Duration.ofSeconds(10), null));
    }

    @Test
    void rejectsAHeartbeatThatIsNotPositive() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LeaderElectionProperties(Duration.ofSeconds(10), Duration.ZERO))
                .withMessageContaining("heartbeat");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LeaderElectionProperties(Duration.ofSeconds(10), Duration.ofSeconds(-1)))
                .withMessageContaining("heartbeat");
    }

    @Test
    void rejectsATimeToLiveThatIsNotPositive() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LeaderElectionProperties(Duration.ZERO, Duration.ofSeconds(-1)))
                .withMessageContaining("lock-time-to-live");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LeaderElectionProperties(Duration.ofSeconds(-10), Duration.ofSeconds(-20)))
                .withMessageContaining("lock-time-to-live");
    }

    @Test
    void rejectsAHeartbeatAboveHalfTheTimeToLive() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LeaderElectionProperties(Duration.ofSeconds(10), Duration.ofSeconds(6)))
                .withMessageContaining("at most half");
    }
}
