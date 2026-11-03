// ABOUTME: Unit tests for MaskingLogRecordExporter: log records leave the process with every IBAN masked.
// ABOUTME: Uses the real OpenTelemetry logs SDK with an in-memory exporter behind the masking decorator.
package dev.thinkahead.sepa;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MaskingLogRecordExporterTest {

    private static final String IBAN = "DE89370400440532013000";
    private static final String MASKED = "DE89**************3000";

    private final InMemoryLogRecordExporter collected = InMemoryLogRecordExporter.create();
    private final SdkLoggerProvider provider = SdkLoggerProvider.builder()
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(new MaskingLogRecordExporter(collected)))
            .build();

    @AfterEach
    void closeProvider() {
        provider.close();
    }

    @Test
    void masksTheBody() {
        emit(builder -> builder.setBody("Invalid IBAN " + IBAN));

        assertThat(single().getBodyValue().asString()).isEqualTo("Invalid IBAN " + MASKED);
    }

    @Test
    void masksAPaperFormatIbanInTheBody() {
        emit(builder -> builder.setBody("IBAN: DE89 3704 0044 0532 0130 00."));

        assertThat(single().getBodyValue().asString()).isEqualTo("IBAN: DE89 **** **** **** **30 00.");
    }

    @Test
    void masksStringAttributesSuchAsExceptionMessageAndStackTrace() {
        emit(builder -> builder.setBody("Delivery failed")
                .setAttribute(AttributeKey.stringKey("exception.message"), "bad account " + IBAN)
                .setAttribute(
                        AttributeKey.stringKey("exception.stacktrace"),
                        "java.lang.IllegalStateException: bad account " + IBAN + "\n\tat Example.run(Example.java:1)"));

        var attributes = single().getAttributes();
        assertThat(attributes.get(AttributeKey.stringKey("exception.message"))).isEqualTo("bad account " + MASKED);
        assertThat(attributes.get(AttributeKey.stringKey("exception.stacktrace")))
                .startsWith("java.lang.IllegalStateException: bad account " + MASKED)
                .doesNotContain(IBAN);
    }

    @Test
    void masksStringArrayAttributes() {
        emit(builder -> builder.setBody("x")
                .setAttribute(AttributeKey.stringArrayKey("accounts"), List.of(IBAN, "no account")));

        assertThat(single().getAttributes().get(AttributeKey.stringArrayKey("accounts")))
                .containsExactly(MASKED, "no account");
    }

    @Test
    void masksStructuredBodies() {
        emit(builder -> builder.setBody(Value.of(Value.of("account " + IBAN), Value.of(42L))));

        assertThat(single().getBodyValue().asString()).contains(MASKED).doesNotContain(IBAN);
    }

    @Test
    void leavesEverythingElseUnchanged() {
        var fileId = "4f3c2a8e-0d5b-4c1e-9a77-2b1f0e6d3c90";
        emit(builder -> builder.setBody("Validated payment file: accepted")
                .setSeverity(Severity.INFO)
                .setSeverityText("INFO")
                .setAttribute(AttributeKey.stringKey("fileId"), fileId)
                .setAttribute(AttributeKey.longKey("attempt"), 2L));

        var record = single();
        assertThat(record.getBodyValue().asString()).isEqualTo("Validated payment file: accepted");
        assertThat(record.getSeverity()).isEqualTo(Severity.INFO);
        assertThat(record.getSeverityText()).isEqualTo("INFO");
        assertThat(record.getAttributes().get(AttributeKey.stringKey("fileId"))).isEqualTo(fileId);
        assertThat(record.getAttributes().get(AttributeKey.longKey("attempt"))).isEqualTo(2L);
    }

    private void emit(java.util.function.UnaryOperator<io.opentelemetry.api.logs.LogRecordBuilder> record) {
        record.apply(provider.get("test").logRecordBuilder()).emit();
    }

    private LogRecordData single() {
        assertThat(collected.getFinishedLogRecordItems()).hasSize(1);
        return collected.getFinishedLogRecordItems().getFirst();
    }
}
