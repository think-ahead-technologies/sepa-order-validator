// ABOUTME: Worker settings per processing step (prefix "sepa.processing", e.g. sepa.processing.deliver.workers).
// ABOUTME: Every step that exists needs its settings; a missing step fails at startup.
package dev.thinkahead.sepa.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sepa.processing")
record ProcessingProperties(StepSettings deliver) {

    ProcessingProperties {
        if (deliver == null) {
            throw new IllegalArgumentException("sepa.processing.deliver.* settings are required");
        }
    }

    StepSettings settings(Step step) {
        return switch (step) {
            case DELIVER -> deliver;
        };
    }
}
