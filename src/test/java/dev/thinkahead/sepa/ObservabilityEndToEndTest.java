// ABOUTME: End-to-end tests of the operational HTTP endpoints of the running application on a real port.
// ABOUTME: Health and Prometheus metrics are exposed, nothing else; a customer left without a reply shows in the
// metrics.
package dev.thinkahead.sepa;

import static dev.thinkahead.sepa.Fixtures.bytes;
import static dev.thinkahead.sepa.Fixtures.uniqueMessageId;
import static dev.thinkahead.sepa.Fixtures.validPain001;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
class ObservabilityEndToEndTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern UNANSWERED =
            Pattern.compile("(?m)^sepa_intake_files_unanswered(?:\\{[^}]*})? (\\S+)$");

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    SftpTestClient customer;

    @Autowired
    JdbcClient jdbc;

    @Test
    void healthIsUpWithoutRevealingDetails() throws Exception {
        HttpResponse<String> health = get("/actuator/health");

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("components");
    }

    @Test
    void livenessAndReadinessProbesAreUp() throws Exception {
        assertThat(get("/actuator/health/liveness").body()).contains("\"status\":\"UP\"");
        assertThat(get("/actuator/health/readiness").body()).contains("\"status\":\"UP\"");
    }

    @Test
    void exposesNoOtherActuatorEndpoints() throws Exception {
        assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
        assertThat(get("/actuator/beans").statusCode()).isEqualTo(404);
        assertThat(get("/actuator/loggers").statusCode()).isEqualTo(404);
    }

    @Test
    void aFileLeftWithoutReplyShowsInTheUnansweredFilesMetric() throws Exception {
        String fileName = "no-reply-" + UUID.randomUUID() + ".xml";
        double before = unansweredFiles();

        customer.chmod(".", "outbound", 0555);
        try {
            customer.upload("inbound", fileName, bytes(validPain001(uniqueMessageId())));
            await().atMost(TIMEOUT)
                    .until(() -> jdbc.sql("select count(*) from payment_file where file_name = ?")
                                    .param(fileName)
                                    .query(Integer.class)
                                    .single()
                            == 1);
            jdbc.sql("update payment_file set received_at = now() - interval '1 hour' where file_name = ?")
                    .param(fileName)
                    .update();

            assertThat(unansweredFiles()).isEqualTo(before + 1);
        } finally {
            customer.chmod(".", "outbound", 0755);
        }
        await().atMost(Duration.ofMinutes(1))
                .untilAsserted(() -> assertThat(unansweredFiles()).isEqualTo(before));
    }

    private double unansweredFiles() throws Exception {
        HttpResponse<String> metrics = get("/actuator/prometheus");
        assertThat(metrics.statusCode()).isEqualTo(200);
        Matcher matcher = UNANSWERED.matcher(metrics.body());
        assertThat(matcher.find())
                .as("sepa_intake_files_unanswered in Prometheus output")
                .isTrue();
        return Double.parseDouble(matcher.group(1));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
