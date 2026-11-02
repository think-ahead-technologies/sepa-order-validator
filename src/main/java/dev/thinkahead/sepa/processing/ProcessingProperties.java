// ABOUTME: Worker settings per processing step (prefix "sepa.processing", e.g. sepa.processing.deliver.workers).
// ABOUTME: Also the retention of DONE work items; every step needs its settings, or startup fails.
package dev.thinkahead.sepa.processing;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sepa.processing")
/**
 * @param retention            how long DONE work items are kept
 * @param housekeepingInterval how often DONE work items past the retention are deleted
 */
record ProcessingProperties(
        StepSettings validate,
        StepSettings report,
        StepSettings deliver,
        Duration retention,
        Duration housekeepingInterval) {

    ProcessingProperties {
        if (validate == null || report == null || deliver == null) {
            throw new IllegalArgumentException(
                    "sepa.processing.validate.*, sepa.processing.report.* and sepa.processing.deliver.* are required");
        }
        if (retention == null || !retention.isPositive()) {
            throw new IllegalArgumentException("sepa.processing.retention must be positive, was " + retention);
        }
        if (housekeepingInterval == null || !housekeepingInterval.isPositive()) {
            throw new IllegalArgumentException(
                    "sepa.processing.housekeeping-interval must be positive, was " + housekeepingInterval);
        }
    }

    StepSettings settings(Step step) {
        return switch (step) {
            case VALIDATE -> validate;
            case REPORT -> report;
            case DELIVER -> deliver;
        };
    }
}
