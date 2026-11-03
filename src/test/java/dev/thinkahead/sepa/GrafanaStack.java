// ABOUTME: Test client for the Loki, Tempo and Prometheus HTTP APIs of the Grafana test stack container.
// ABOUTME: Lets tests assert on what the collector actually stored: log lines, spans and metric series.
package dev.thinkahead.sepa;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.testcontainers.grafana.LgtmStackContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class GrafanaStack {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final LgtmStackContainer container;
    private final HttpClient http = HttpClient.newHttpClient();

    public GrafanaStack(LgtmStackContainer container) {
        this.container = container;
    }

    /**
     * A log line stored in Loki: its text and its labels, including structured metadata such as trace_id.
     */
    public record LogLine(String line, JsonNode labels) {}

    /**
     * Log lines of the last hour matching a LogQL query, e.g. {service_name="x"} |= "text".
     */
    public List<LogLine> logs(String logQl) {
        long now = Instant.now().toEpochMilli() * 1_000_000;
        long hourAgo = now - Duration.ofHours(1).toNanos();
        JsonNode response = get(container.getLokiUrl() + "/loki/api/v1/query_range?limit=1000&start=" + hourAgo
                + "&end=" + now + "&query=" + encode(logQl));
        List<LogLine> lines = new ArrayList<>();
        for (JsonNode stream : response.path("data").path("result")) {
            for (JsonNode value : stream.path("values")) {
                lines.add(new LogLine(value.get(1).asString(), stream.path("stream")));
            }
        }
        return lines;
    }

    /**
     * A trace by id as Tempo returns it (OTLP JSON); without spans while Tempo does not know it (yet).
     */
    public JsonNode trace(String traceId) {
        return get(container.getTempoUrl() + "/api/v2/traces/" + traceId);
    }

    public static boolean hasSpans(JsonNode trace) {
        return !trace.path("trace").path("resourceSpans").isEmpty();
    }

    /**
     * Trace ids of the last hour matching a TraceQL query, e.g. { span.sepa.file.id = "x" }.
     */
    public List<String> traceIds(String traceQl) {
        long now = Instant.now().getEpochSecond();
        JsonNode response = get(container.getTempoUrl() + "/api/search?limit=100&start=" + (now - 3600) + "&end="
                + (now + 60) + "&q=" + encode(traceQl));
        return response.path("traces")
                .valueStream()
                .map(trace -> trace.path("traceID").asString())
                .toList();
    }

    /**
     * The series of a PromQL instant query.
     */
    public List<JsonNode> metrics(String promQl) {
        JsonNode response = get(container.getPrometheusHttpUrl() + "/api/v1/query?query=" + encode(promQl));
        return response.path("data").path("result").valueStream().toList();
    }

    private JsonNode get(String url) {
        try {
            HttpResponse<String> response =
                    http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.body().isBlank()) {
                return JSON.createObjectNode();
            }
            return JSON.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Request to the Grafana stack failed: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String query) {
        return URLEncoder.encode(query, StandardCharsets.UTF_8);
    }
}
