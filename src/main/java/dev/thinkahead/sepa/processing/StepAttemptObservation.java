// ABOUTME: Observes one attempt of a processing step: a span with the file id and the timer sepa.processing.step,
// ABOUTME: both tagged with the step and the attempt's outcome (spec observability).
package dev.thinkahead.sepa.processing;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Locale;
import java.util.UUID;

final class StepAttemptObservation {

    static final String NAME = "sepa.processing.step";

    enum Outcome {
        COMPLETED,
        RETRIED,
        FAILED;

        String tag() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Observation observation;

    private StepAttemptObservation(Observation observation) {
        this.observation = observation;
    }

    static StepAttemptObservation start(ObservationRegistry registry, Step step, UUID fileId) {
        Observation observation = Observation.createNotStarted(NAME, registry)
                .contextualName(NAME + " " + step.name().toLowerCase(Locale.ROOT))
                .lowCardinalityKeyValue("sepa.step", step.name().toLowerCase(Locale.ROOT))
                // The file id is unbounded: a span attribute only, never a metric tag.
                .highCardinalityKeyValue("sepa.file.id", fileId.toString())
                .start();
        return new StepAttemptObservation(observation);
    }

    /**
     * Makes the attempt the current observation (and span) on the calling thread until the scope is closed.
     */
    Observation.Scope openScope() {
        return observation.openScope();
    }

    void end(Outcome outcome, Throwable error) {
        observation.lowCardinalityKeyValue("sepa.outcome", outcome.tag());
        if (error != null) {
            observation.error(error);
        }
        observation.stop();
    }
}
