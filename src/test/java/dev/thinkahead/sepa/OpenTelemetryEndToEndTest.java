// ABOUTME: End-to-end tests of OpenTelemetry export: a file dropped on SFTP shows up in a real Grafana stack as
// ABOUTME: receive and step spans found by file id, correlated Loki lines and step metrics, never with its IBANs.
package dev.thinkahead.sepa;

import static dev.thinkahead.sepa.Fixtures.bytes;
import static dev.thinkahead.sepa.Fixtures.uniqueMessageId;
import static dev.thinkahead.sepa.Fixtures.validPain001;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.grafana.LgtmStackContainer;
import tools.jackson.databind.JsonNode;

@SpringBootTest(
        properties = {
            TelemetryTestConfiguration.METRICS_EXPORT,
            TelemetryTestConfiguration.METRICS_STEP,
            TelemetryTestConfiguration.TRACES_DELAY,
            TelemetryTestConfiguration.LOGS_DELAY
        })
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class, TelemetryTestConfiguration.class})
class OpenTelemetryEndToEndTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final String SERVICE = "payment-order-validator";

    @Autowired
    SftpTestClient customer;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    LgtmStackContainer lgtm;

    @Test
    void aProcessedFileIsTracedLoggedAndMeasured(CapturedOutput output) throws Exception {
        var grafana = new GrafanaStack(lgtm);
        UUID fileId = submitAndAwaitReply(validPain001(uniqueMessageId()));

        Map<String, String> outcomeByStep =
                await().atMost(TIMEOUT).until(() -> stepOutcomes(grafana, fileId), found -> found.size() == 3);
        assertThat(outcomeByStep)
                .containsEntry("validate", "completed")
                .containsEntry("report", "completed")
                .containsEntry("deliver", "completed");
        assertThat(spanNames(grafana, fileId)).contains("sepa.intake.receive");

        GrafanaStack.LogLine validated = await().atMost(TIMEOUT)
                .until(
                        () -> grafana.logs("{service_name=\"" + SERVICE + "\"} | fileId=\"" + fileId
                                + "\" |= \"Validated payment file\""),
                        found -> !found.isEmpty())
                .getFirst();
        String traceId = validated.labels().path("trace_id").asString("");
        assertThat(spansOf(grafana.trace(traceId)))
                .anySatisfy(span -> assertThat(attribute(span, "sepa.step")).isEqualTo("validate"));

        await().atMost(TIMEOUT)
                .until(
                        () -> grafana.metrics("sepa_processing_step_seconds_count{job=\"" + SERVICE
                                + "\",sepa_outcome=\"completed\"}"),
                        series -> series.stream()
                                        .map(s -> s.path("metric")
                                                .path("sepa_step")
                                                .asString())
                                        .distinct()
                                        .count()
                                == 3);

        assertThat(LogLines.forFile(output, fileId))
                .filteredOn(line -> line.path("message").asString().startsWith("Validated payment file"))
                .allSatisfy(line -> {
                    assertThat(line.path("traceId").asString()).isEqualTo(traceId);
                    assertThat(line.path("spanId").asString()).isNotBlank();
                });
    }

    @Test
    void theIbanOfARejectedFileNeverReachesTheStack() throws Exception {
        var grafana = new GrafanaStack(lgtm);
        UUID fileId = submitAndAwaitReply(
                validPain001(uniqueMessageId()).replace("NL91ABNA0417164300", "NL91ABNA0417164301"));

        List<String> traceIds =
                await().atMost(TIMEOUT).until(() -> traceIdsOf(grafana, fileId), found -> found.size() >= 4);

        assertThat(traceIds)
                .allSatisfy(
                        traceId -> assertThat(grafana.trace(traceId).toString()).doesNotContain("NL91ABNA0417164301"));
        assertThat(grafana.logs("{service_name=\"" + SERVICE + "\"} |= \"NL91ABNA0417164301\""))
                .isEmpty();
        assertThat(grafana.logs("{service_name=\"" + SERVICE + "\"} | fileId=\"" + fileId + "\""))
                .isNotEmpty();
    }

    private UUID submitAndAwaitReply(String pain001) throws Exception {
        String fileName = "e2e-otel-" + UUID.randomUUID() + ".xml";
        customer.upload("inbound", fileName, bytes(pain001));
        return await().atMost(TIMEOUT)
                .until(
                        () -> jdbc.sql("select id from payment_file where file_name = ? and replied_at is not null")
                                .param(fileName)
                                .query(UUID.class)
                                .optional(),
                        java.util.Optional::isPresent)
                .orElseThrow();
    }

    private List<String> traceIdsOf(GrafanaStack grafana, UUID fileId) {
        return grafana.traceIds("{ span.sepa.file.id = \"" + fileId + "\" }");
    }

    private List<JsonNode> spansOfFile(GrafanaStack grafana, UUID fileId) {
        return traceIdsOf(grafana, fileId).stream()
                .flatMap(traceId -> spansOf(grafana.trace(traceId)).stream())
                .filter(span -> fileId.toString().equals(attribute(span, "sepa.file.id")))
                .toList();
    }

    private Map<String, String> stepOutcomes(GrafanaStack grafana, UUID fileId) {
        Map<String, String> outcomes = new java.util.HashMap<>();
        for (JsonNode span : spansOfFile(grafana, fileId)) {
            String step = attribute(span, "sepa.step");
            if (step != null) {
                outcomes.put(step, attribute(span, "sepa.outcome"));
            }
        }
        return outcomes;
    }

    private List<String> spanNames(GrafanaStack grafana, UUID fileId) {
        return spansOfFile(grafana, fileId).stream()
                .map(span -> span.path("name").asString())
                .toList();
    }

    private static List<JsonNode> spansOf(JsonNode trace) {
        return trace.path("trace")
                .path("resourceSpans")
                .valueStream()
                .flatMap(resource -> resource.path("scopeSpans").valueStream())
                .flatMap(scope -> scope.path("spans").valueStream())
                .toList();
    }

    private static String attribute(JsonNode span, String key) {
        return span.path("attributes")
                .valueStream()
                .filter(attribute -> key.equals(attribute.path("key").asString()))
                .map(attribute -> attribute.path("value").path("stringValue").asString())
                .findFirst()
                .orElse(null);
    }
}
