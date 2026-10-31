// ABOUTME: Enables the processing module's settings; the workers themselves are started by StepWorkers.
// ABOUTME: See ADR-0017 for the work queue design.
package dev.thinkahead.sepa.processing;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProcessingProperties.class)
class ProcessingConfiguration {
}
