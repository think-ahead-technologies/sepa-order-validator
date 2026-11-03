// ABOUTME: Integration test of the OTLP export against a real Grafana stack: logs reach Loki, spans reach Tempo,
// ABOUTME: metrics reach Prometheus, all tagged with the service name, linked by trace id and IBAN-masked.
package dev.thinkahead.sepa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.grafana.LgtmStackContainer;
import tools.jackson.databind.JsonNode;

@SpringBootTest(
        properties = {
            TelemetryTestConfiguration.METRICS_EXPORT,
            TelemetryTestConfiguration.METRICS_STEP,
            TelemetryTestConfiguration.TRACES_DELAY,
            TelemetryTestConfiguration.LOGS_DELAY
        })
@Import({TestcontainersConfiguration.class, TelemetryTestConfiguration.class})
class OpenTelemetryExportIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(OpenTelemetryExportIntegrationTest.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final String SERVICE = "payment-order-validator";
    private static final String IBAN = "DE89370400440532013000";
    private static final String MASKED = "DE89**************3000";

    @Autowired
    ObservationRegistry observations;

    @Autowired
    LgtmStackContainer lgtm;

    @Test
    void logsAndSpansReachTheStackLinkedByTraceIdAndMasked() {
        String probe = "otlp-probe-" + UUID.randomUUID();
        var grafana = new GrafanaStack(lgtm);

        Observation.createNotStarted("otlp.probe", observations)
                .highCardinalityKeyValue("probe", probe)
                .observe(() -> {
                    LOG.info("Probe {} for account {}", probe, IBAN);
                    observations
                            .getCurrentObservation()
                            .error(new IllegalStateException("Probe failure for account " + IBAN));
                });

        List<GrafanaStack.LogLine> lines = await().atMost(TIMEOUT)
                .until(
                        () -> grafana.logs("{service_name=\"" + SERVICE + "\"} |= \"" + probe + "\""),
                        found -> !found.isEmpty());
        GrafanaStack.LogLine line = lines.getFirst();
        assertThat(line.line()).contains(MASKED).doesNotContain(IBAN);
        String traceId = line.labels().path("trace_id").asString("");
        assertThat(traceId).isNotBlank();

        JsonNode trace = await().atMost(TIMEOUT).until(() -> grafana.trace(traceId), GrafanaStack::hasSpans);
        String json = trace.toString();
        assertThat(json).contains("otlp.probe").contains(probe).contains(MASKED).doesNotContain(IBAN);
    }

    @Test
    void metricsReachTheStackUnderTheServiceName() {
        var grafana = new GrafanaStack(lgtm);

        await().atMost(TIMEOUT)
                .until(
                        () -> grafana.metrics("sepa_intake_files_unanswered{job=\"" + SERVICE + "\"}"),
                        series -> !series.isEmpty());
    }
}
