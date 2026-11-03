// ABOUTME: Starts the application locally with PostgreSQL, SFTP and a Grafana stack from Testcontainers (bootTestRun).
// ABOUTME: Drop pain.001 files into the container's inbound directory and watch logs, traces and metrics in Grafana.
package dev.thinkahead.sepa;

import java.util.stream.Stream;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.testcontainers.grafana.LgtmStackContainer;

public class TestPaymentOrderValidatorApplication {

    public static void main(String[] args) {
        String[] withMetricExport = Stream.concat(
                        Stream.of(args), Stream.of("--" + TelemetryTestConfiguration.METRICS_EXPORT))
                .toArray(String[]::new);
        var application = SpringApplication.from(PaymentOrderValidatorApplication::main)
                .with(TestcontainersConfiguration.class, TelemetryTestConfiguration.class)
                .run(withMetricExport);
        String grafana = application
                .getApplicationContext()
                .getBean(LgtmStackContainer.class)
                .getGrafanaHttpUrl();
        LoggerFactory.getLogger(TestPaymentOrderValidatorApplication.class)
                .info("Grafana with logs, traces and metrics of this run: {}", grafana);
    }
}
