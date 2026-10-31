// ABOUTME: Worker settings per processing step (prefix "sepa.processing", e.g. sepa.processing.deliver.workers).
// ABOUTME: Every step that exists needs its settings; a missing step fails at startup.
package dev.thinkahead.sepa.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sepa.processing")
record ProcessingProperties(StepSettings validate, StepSettings report, StepSettings deliver) {

    ProcessingProperties {
        if (validate == null || report == null || deliver == null) {
            throw new IllegalArgumentException(
                    "sepa.processing.validate.*, sepa.processing.report.* and sepa.processing.deliver.* are required");
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
