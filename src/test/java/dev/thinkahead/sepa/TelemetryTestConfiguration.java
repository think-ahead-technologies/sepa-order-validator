// ABOUTME: A real Grafana stack (OpenTelemetry collector, Loki, Tempo, Prometheus, Grafana) in one container,
// ABOUTME: connected via Spring Boot service connections so logs, traces and metrics are exported to it over OTLP.
package dev.thinkahead.sepa;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TelemetryTestConfiguration {

    /**
     * Pinned by tag and digest; Testcontainers only accepts a digest as a declared substitute of the plain name.
     */
    static final DockerImageName LGTM_IMAGE = DockerImageName.parse(
                    "grafana/otel-lgtm:0.39.0@sha256:4c8056c9c05fcb3c8fd93800e85334bfd79b4d0271b47a54a121a64af41fe1fe")
            .asCompatibleSubstituteFor("grafana/otel-lgtm");

    @Bean
    @ServiceConnection
    LgtmStackContainer lgtm() {
        return new LgtmStackContainer(LGTM_IMAGE);
    }

    /**
     * For {@code @SpringBootTest(properties = ...)}: metric export is off by default (switched on in production by
     * OTEL_METRICS_EXPORTER=otlp) and decided by a condition, so it must be set before the context is refreshed;
     * short export intervals let tests see exported data within seconds.
     */
    public static final String METRICS_EXPORT = "management.otlp.metrics.export.enabled=true";

    public static final String METRICS_STEP = "management.otlp.metrics.export.step=1s";
    public static final String TRACES_DELAY = "management.opentelemetry.tracing.export.schedule-delay=200ms";
    public static final String LOGS_DELAY = "management.opentelemetry.logging.export.schedule-delay=200ms";
}
