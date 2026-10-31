// ABOUTME: Worker settings per processing step (prefix "sepa.processing", e.g. sepa.processing.deliver.workers).
// ABOUTME: Every step that exists needs its settings; a missing step fails at startup.
package dev.thinkahead.sepa.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sepa.processing")
record ProcessingProperties(StepSettings validate, StepSettings deliver) {

    ProcessingProperties {
        if (validate == null || deliver == null) {
            throw new IllegalArgumentException("sepa.processing.validate.* and sepa.processing.deliver.* are required");
        }
    }

    StepSettings settings(Step step) {
        return switch (step) {
            case VALIDATE -> validate;
            case DELIVER -> deliver;
        };
    }
}
