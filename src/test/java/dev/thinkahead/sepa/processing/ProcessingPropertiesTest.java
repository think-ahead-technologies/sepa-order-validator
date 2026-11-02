// ABOUTME: Unit tests for the processing settings: every step needs its settings, retention must be positive.
// ABOUTME: Invalid settings fail at startup instead of at the first work item.
package dev.thinkahead.sepa.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProcessingPropertiesTest {

    private static final StepSettings STEP = new StepSettings(
            2,
            Duration.ofMinutes(2),
            Duration.ofMinutes(1),
            10,
            Duration.ofSeconds(1),
            Duration.ofSeconds(10),
            Duration.ofMinutes(10));

    @Test
    void returnsTheSettingsOfEachStep() {
        var properties = new ProcessingProperties(STEP, STEP, STEP, Duration.ofDays(7), Duration.ofHours(1));

        assertThat(properties.settings(Step.REPORT)).isSameAs(STEP);
        assertThat(properties.retention()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void requiresSettingsForEveryStep() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProcessingProperties(STEP, null, STEP, Duration.ofDays(7), Duration.ofHours(1)));
    }

    @Test
    void requiresAPositiveRetentionAndHousekeepingInterval() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProcessingProperties(STEP, STEP, STEP, Duration.ZERO, Duration.ofHours(1)))
                .withMessageContaining("retention");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProcessingProperties(STEP, STEP, STEP, Duration.ofDays(7), null))
                .withMessageContaining("housekeeping-interval");
    }
}
