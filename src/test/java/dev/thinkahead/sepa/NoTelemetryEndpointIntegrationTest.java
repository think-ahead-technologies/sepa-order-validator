// ABOUTME: Integration test that without a configured collector endpoint nothing is exported over OTLP:
// ABOUTME: no OTLP exporter or registry exists, so no request is sent and no failed export is ever logged.
package dev.thinkahead.sepa;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NoTelemetryEndpointIntegrationTest {

    @Autowired
    ApplicationContext context;

    @Test
    void noOtlpExporterOrRegistryExists() {
        assertThat(context.getBeansOfType(SpanExporter.class)).isEmpty();
        assertThat(context.getBeansOfType(LogRecordExporter.class)).isEmpty();
        assertThat(context.getBeansOfType(MeterRegistry.class).values())
                .noneMatch(registry -> registry.getClass().getSimpleName().startsWith("Otlp"));
    }
}
