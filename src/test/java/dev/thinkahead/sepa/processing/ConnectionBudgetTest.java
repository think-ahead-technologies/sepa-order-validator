// ABOUTME: Unit tests for the connection budget: the pool must cover every step's workers plus fixed headroom.
// ABOUTME: A pool that is too small fails at startup with the numbers needed to fix it.
package dev.thinkahead.sepa.processing;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectionBudgetTest {

    private static final List<StepSettings> THREE_STEPS_WITH_TWO_WORKERS =
            List.of(settings(2), settings(2), settings(2));

    @Test
    void acceptsAPoolThatCoversAllWorkersAndTheHeadroom() {
        assertThatNoException().isThrownBy(() -> ConnectionBudget.check(6 + ConnectionBudget.HEADROOM,
                THREE_STEPS_WITH_TWO_WORKERS));
    }

    @Test
    void rejectsAPoolSmallerThanAllWorkersPlusHeadroom() {
        assertThatIllegalStateException()
                .isThrownBy(() -> ConnectionBudget.check(6 + ConnectionBudget.HEADROOM - 1,
                        THREE_STEPS_WITH_TWO_WORKERS))
                .withMessageContaining("spring.datasource.hikari.maximum-pool-size")
                .withMessageContaining(String.valueOf(6 + ConnectionBudget.HEADROOM));
    }

    private static StepSettings settings(int workers) {
        return new StepSettings(workers, Duration.ofMinutes(2), Duration.ofMinutes(1), 10, Duration.ofSeconds(1),
                Duration.ofSeconds(10), Duration.ofMinutes(10));
    }
}
