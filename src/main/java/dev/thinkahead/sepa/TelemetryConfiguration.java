// ABOUTME: Wires OpenTelemetry export: wraps every log record and span exporter in its IBAN-masking decorator
// ABOUTME: and connects the Logback OpenTelemetryAppender to the OpenTelemetry SDK once the context is ready.
package dev.thinkahead.sepa;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TelemetryConfiguration {

    /**
     * Static, so it is registered before the exporter beans it decorates are created.
     */
    @Bean
    static BeanPostProcessor maskingExporters() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return switch (bean) {
                    case MaskingLogRecordExporter masked -> masked;
                    case MaskingSpanExporter masked -> masked;
                    case LogRecordExporter exporter -> new MaskingLogRecordExporter(exporter);
                    case SpanExporter exporter -> new MaskingSpanExporter(exporter);
                    default -> bean;
                };
            }
        };
    }

    /**
     * Log records written before the application is ready are only on the console; the appender drops them.
     */
    @Bean
    ApplicationListener<ApplicationReadyEvent> openTelemetryAppenderInstaller(OpenTelemetry openTelemetry) {
        return event -> OpenTelemetryAppender.install(openTelemetry);
    }
}
