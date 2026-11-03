// ABOUTME: Decorates an OpenTelemetry span exporter so names, attributes, statuses and events leave IBAN-masked.
// ABOUTME: Recorded exceptions are exported from their masked attributes only, never from the original throwable.
package dev.thinkahead.sepa;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.List;

final class MaskingSpanExporter implements SpanExporter {

    private final SpanExporter delegate;

    MaskingSpanExporter(SpanExporter delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        return delegate.export(spans.stream().<SpanData>map(MaskedSpan::new).toList());
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    @Override
    public String toString() {
        return "MaskingSpanExporter{" + delegate + "}";
    }

    private static final class MaskedSpan extends DelegatingSpanData {

        private final String name;
        private final Attributes attributes;
        private final StatusData status;
        private final List<EventData> events;

        MaskedSpan(SpanData span) {
            super(span);
            this.name = LogMasking.mask(span.getName());
            this.attributes = OtlpMasking.mask(span.getAttributes());
            StatusData original = span.getStatus();
            this.status = StatusData.create(original.getStatusCode(), LogMasking.mask(original.getDescription()));
            this.events = span.getEvents().stream()
                    .map(event -> EventData.create(
                            event.getEpochNanos(),
                            LogMasking.mask(event.getName()),
                            OtlpMasking.mask(event.getAttributes()),
                            event.getTotalAttributeCount()))
                    .toList();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Attributes getAttributes() {
            return attributes;
        }

        @Override
        public StatusData getStatus() {
            return status;
        }

        @Override
        public List<EventData> getEvents() {
            return events;
        }
    }
}
