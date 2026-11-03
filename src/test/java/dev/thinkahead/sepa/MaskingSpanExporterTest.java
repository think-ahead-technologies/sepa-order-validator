// ABOUTME: Unit tests for MaskingSpanExporter: spans leave the process with every IBAN masked.
// ABOUTME: Uses the real OpenTelemetry trace SDK with an in-memory exporter behind the masking decorator.
package dev.thinkahead.sepa;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.ExceptionEventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MaskingSpanExporterTest {

    private static final String IBAN = "DE89370400440532013000";
    private static final String MASKED = "DE89**************3000";

    private final InMemorySpanExporter collected = InMemorySpanExporter.create();
    private final SdkTracerProvider provider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(new MaskingSpanExporter(collected)))
            .build();

    @AfterEach
    void closeProvider() {
        provider.close();
    }

    @Test
    void masksNameAttributesAndStatusDescription() {
        Span span = provider.get("test").spanBuilder("check " + IBAN).startSpan();
        span.setAttribute("account", IBAN);
        span.setStatus(StatusCode.ERROR, "rejected " + IBAN);
        span.end();

        SpanData exported = single();
        assertThat(exported.getName()).isEqualTo("check " + MASKED);
        assertThat(exported.getAttributes().get(AttributeKey.stringKey("account")))
                .isEqualTo(MASKED);
        assertThat(exported.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(exported.getStatus().getDescription()).isEqualTo("rejected " + MASKED);
    }

    @Test
    void masksRecordedExceptionsWithoutKeepingTheThrowable() {
        Span span = provider.get("test").spanBuilder("deliver").startSpan();
        span.recordException(new IllegalStateException("bad account " + IBAN));
        span.end();

        EventData event = single().getEvents().getFirst();
        assertThat(event).isNotInstanceOf(ExceptionEventData.class);
        assertThat(event.getAttributes().get(AttributeKey.stringKey("exception.message")))
                .isEqualTo("bad account " + MASKED);
        assertThat(event.getAttributes().get(AttributeKey.stringKey("exception.stacktrace")))
                .contains(MASKED)
                .doesNotContain(IBAN);
    }

    @Test
    void masksEventNamesAndAttributes() {
        Span span = provider.get("test").spanBuilder("deliver").startSpan();
        span.addEvent("retry " + IBAN, Attributes.of(AttributeKey.stringKey("reason"), "account " + IBAN));
        span.end();

        EventData event = single().getEvents().getFirst();
        assertThat(event.getName()).isEqualTo("retry " + MASKED);
        assertThat(event.getAttributes().get(AttributeKey.stringKey("reason"))).isEqualTo("account " + MASKED);
    }

    @Test
    void leavesSpansWithoutIbansUnchanged() {
        Span span = provider.get("test").spanBuilder("sepa.processing.step").startSpan();
        span.setAttribute("sepa.step", "validate");
        span.setAttribute("attempt", 1L);
        span.end();

        SpanData exported = single();
        assertThat(exported.getName()).isEqualTo("sepa.processing.step");
        assertThat(exported.getAttributes().get(AttributeKey.stringKey("sepa.step")))
                .isEqualTo("validate");
        assertThat(exported.getAttributes().get(AttributeKey.longKey("attempt")))
                .isEqualTo(1L);
        assertThat(exported.getSpanContext()).isEqualTo(span.getSpanContext());
    }

    private SpanData single() {
        assertThat(collected.getFinishedSpanItems()).hasSize(1);
        return collected.getFinishedSpanItems().getFirst();
    }
}
