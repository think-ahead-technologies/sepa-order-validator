// ABOUTME: Unit tests for per-step worker settings: backoff growth and validation of the configured values.
// ABOUTME: Backoff doubles per attempt up to its maximum; invalid settings, e.g. a timeout above the lease, fail.
package dev.thinkahead.sepa.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class StepSettingsTest {

    private static final StepSettings SETTINGS = new StepSettings(
            2,
            Duration.ofMinutes(2),
            Duration.ofMinutes(1),
            10,
            Duration.ofSeconds(1),
            Duration.ofSeconds(10),
            Duration.ofMinutes(10));

    @Test
    void backoffDoublesWithEveryFailedAttempt() {
        assertThat(SETTINGS.backoffAfter(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(SETTINGS.backoffAfter(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(SETTINGS.backoffAfter(4)).isEqualTo(Duration.ofSeconds(80));
    }

    @Test
    void backoffIsCappedAtItsMaximum() {
        assertThat(SETTINGS.backoffAfter(7)).isEqualTo(Duration.ofMinutes(10));
        assertThat(SETTINGS.backoffAfter(1_000)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void requiresAtLeastOneWorkerAndOneAttempt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StepSettings(
                        0,
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(1),
                        10,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(10)))
                .withMessageContaining("workers");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StepSettings(
                        2,
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(1),
                        0,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(10)))
                .withMessageContaining("max-attempts");
    }

    @Test
    void requiresPositiveDurations() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StepSettings(
                        2,
                        Duration.ZERO,
                        Duration.ofMinutes(1),
                        10,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(10)))
                .withMessageContaining("lease");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StepSettings(
                        2,
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(1),
                        10,
                        null,
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(10)))
                .withMessageContaining("poll-interval");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StepSettings(
                        2,
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(1),
                        10,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(-1),
                        Duration.ofMinutes(10)))
                .withMessageContaining("initial-backoff");
    }

    @Test
    void stopsAnAttemptHalfwayBetweenItsTimeoutAndTheEndOfTheLease() {
        assertThat(SETTINGS.hardStopAfter()).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void requiresTheTimeoutToBePositiveAndBelowTheLease() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StepSettings(
                        2,
                        Duration.ofMinutes(2),
                        Duration.ZERO,
                        10,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(10)))
                .withMessageContaining("timeout");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StepSettings(
                        2,
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(2),
                        10,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(10)))
                .withMessageContaining("lease");
    }

    @Test
    void requiresTheMaximumBackoffToBeAtLeastTheInitialOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StepSettings(
                        2,
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(1),
                        10,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(5)))
                .withMessageContaining("max-backoff");
    }
}
