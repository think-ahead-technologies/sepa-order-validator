// ABOUTME: Unit tests for StepAttemptObservation: each step attempt is one observation named sepa.processing.step,
// ABOUTME: tagged with step and outcome, carrying the file id only as a span attribute, never as a metric tag.
package dev.thinkahead.sepa.processing;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import dev.thinkahead.sepa.processing.StepAttemptObservation.Outcome;
import io.micrometer.observation.tck.TestObservationRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StepAttemptObservationTest {

    private final TestObservationRegistry registry = TestObservationRegistry.create();
    private final UUID fileId = UUID.fromString("4f3c2a8e-0d5b-4c1e-9a77-2b1f0e6d3c90");

    @Test
    void aCompletedAttemptIsObservedWithStepOutcomeAndFileId() {
        var attempt = StepAttemptObservation.start(registry, Step.VALIDATE, fileId);
        attempt.end(Outcome.COMPLETED, null);

        assertThat(registry)
                .hasSingleObservationThat()
                .hasNameEqualTo("sepa.processing.step")
                .hasLowCardinalityKeyValue("sepa.step", "validate")
                .hasLowCardinalityKeyValue("sepa.outcome", "completed")
                .hasHighCardinalityKeyValue("sepa.file.id", fileId.toString())
                .doesNotHaveError()
                .hasBeenStopped();
    }

    @Test
    void aRetriedAttemptCarriesItsError() {
        var failure = new IllegalStateException("outbound channel unavailable");

        var attempt = StepAttemptObservation.start(registry, Step.DELIVER, fileId);
        attempt.end(Outcome.RETRIED, failure);

        assertThat(registry)
                .hasSingleObservationThat()
                .hasLowCardinalityKeyValue("sepa.step", "deliver")
                .hasLowCardinalityKeyValue("sepa.outcome", "retried")
                .hasError(failure)
                .hasBeenStopped();
    }

    @Test
    void aFailedAttemptIsObservedAsFailed() {
        var attempt = StepAttemptObservation.start(registry, Step.REPORT, fileId);
        attempt.end(Outcome.FAILED, null);

        assertThat(registry)
                .hasSingleObservationThat()
                .hasLowCardinalityKeyValue("sepa.step", "report")
                .hasLowCardinalityKeyValue("sepa.outcome", "failed")
                .hasBeenStopped();
    }

    @Test
    void theAttemptIsCurrentWhileItsScopeIsOpenOnAnyThread() throws Exception {
        var attempt = StepAttemptObservation.start(registry, Step.VALIDATE, fileId);
        var seenOnOtherThread = new java.util.concurrent.atomic.AtomicReference<Object>();

        Thread.ofVirtual()
                .start(() -> {
                    try (var ignored = attempt.openScope()) {
                        seenOnOtherThread.set(registry.getCurrentObservation());
                    }
                })
                .join();
        attempt.end(Outcome.COMPLETED, null);

        assertThat(seenOnOtherThread.get()).isNotNull();
        assertThat(registry.getCurrentObservation()).isNull();
    }
}
