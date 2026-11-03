// ABOUTME: Decorates an OpenTelemetry log record exporter so bodies and attributes leave the process IBAN-masked.
// ABOUTME: Safety net for the OTLP log path; code must still never log values from a payment file (spec observability).
package dev.thinkahead.sepa;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.Body;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Collection;

final class MaskingLogRecordExporter implements LogRecordExporter {

    private final LogRecordExporter delegate;

    MaskingLogRecordExporter(LogRecordExporter delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableResultCode export(Collection<LogRecordData> logs) {
        return delegate.export(
                logs.stream().<LogRecordData>map(MaskedLogRecord::new).toList());
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
        return "MaskingLogRecordExporter{" + delegate + "}";
    }

    private static final class MaskedLogRecord implements LogRecordData {

        private final LogRecordData record;
        private final Value<?> body;
        private final Attributes attributes;

        MaskedLogRecord(LogRecordData record) {
            this.record = record;
            this.body = OtlpMasking.mask(record.getBodyValue());
            this.attributes = OtlpMasking.mask(record.getAttributes());
        }

        @Override
        public Resource getResource() {
            return record.getResource();
        }

        @Override
        public InstrumentationScopeInfo getInstrumentationScopeInfo() {
            return record.getInstrumentationScopeInfo();
        }

        @Override
        public long getTimestampEpochNanos() {
            return record.getTimestampEpochNanos();
        }

        @Override
        public long getObservedTimestampEpochNanos() {
            return record.getObservedTimestampEpochNanos();
        }

        @Override
        public SpanContext getSpanContext() {
            return record.getSpanContext();
        }

        @Override
        public Severity getSeverity() {
            return record.getSeverity();
        }

        @Override
        public String getSeverityText() {
            return record.getSeverityText();
        }

        @Override
        @SuppressWarnings("deprecation") // part of the interface the exporters still implement against
        public Body getBody() {
            return body == null ? Body.empty() : Body.string(body.asString());
        }

        @Override
        public Value<?> getBodyValue() {
            return body;
        }

        @Override
        public Attributes getAttributes() {
            return attributes;
        }

        @Override
        public int getTotalAttributeCount() {
            return record.getTotalAttributeCount();
        }

        @Override
        public String getEventName() {
            return record.getEventName();
        }
    }
}
