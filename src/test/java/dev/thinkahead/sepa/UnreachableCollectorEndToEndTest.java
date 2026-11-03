// ABOUTME: End-to-end test with a configured but unreachable OTLP collector: files are still processed and
// ABOUTME: answered; only the exporters report the failed exports, nothing else logs at level ERROR.
package dev.thinkahead.sepa;

import static dev.thinkahead.sepa.Fixtures.bytes;
import static dev.thinkahead.sepa.Fixtures.uniqueMessageId;
import static dev.thinkahead.sepa.Fixtures.validPain001;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
        properties = {
            TelemetryTestConfiguration.METRICS_EXPORT,
            TelemetryTestConfiguration.METRICS_STEP,
            TelemetryTestConfiguration.TRACES_DELAY,
            TelemetryTestConfiguration.LOGS_DELAY
        })
@ExtendWith(OutputCaptureExtension.class)
// Closed afterwards: a cached context would keep failing its exports into the output of later tests.
@DirtiesContext
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
class UnreachableCollectorEndToEndTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String COLLECTOR = "http://127.0.0.1:" + closedPort();

    @DynamicPropertySource
    static void unreachableCollector(DynamicPropertyRegistry registry) {
        registry.add("management.opentelemetry.tracing.export.otlp.endpoint", () -> COLLECTOR + "/v1/traces");
        registry.add("management.opentelemetry.logging.export.otlp.endpoint", () -> COLLECTOR + "/v1/logs");
        registry.add("management.otlp.metrics.export.url", () -> COLLECTOR + "/v1/metrics");
    }

    @Autowired
    SftpTestClient customer;

    @Autowired
    JdbcClient jdbc;

    @Test
    void filesAreProcessedAndOnlyTheExportersReportErrors(CapturedOutput output) throws Exception {
        String fileName = "e2e-no-collector-" + UUID.randomUUID() + ".xml";
        customer.upload("inbound", fileName, bytes(validPain001(uniqueMessageId())));

        await().atMost(TIMEOUT)
                .until(() ->
                        jdbc.sql("select count(*) from payment_file where file_name = ? and replied_at is not null")
                                        .param(fileName)
                                        .query(Integer.class)
                                        .single()
                                == 1);
        // The exporters retry with backoff before they report a failure: wait until each signal has reported one.
        await().atMost(TIMEOUT)
                .until(() -> output.getAll().contains("Failed to export spans")
                        && output.getAll().contains("Failed to export logs")
                        && output.getAll().contains("Failed to publish metrics"));

        assertThat(LogLines.of(output))
                .filteredOn(line -> line.path("log").path("level").asString().equals("ERROR"))
                .isNotEmpty()
                .allSatisfy(line -> assertThat(line.path("log").path("logger").asString())
                        .startsWith("io.opentelemetry.exporter."));
    }

    private static int closedPort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
