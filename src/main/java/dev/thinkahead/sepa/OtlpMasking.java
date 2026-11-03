// ABOUTME: Masks IBANs in OpenTelemetry attributes and values with LogMasking.mask, before they are exported.
// ABOUTME: Shared by the log record and span exporter decorators, so OTLP gets the same safety net as the console.
package dev.thinkahead.sepa;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import java.util.List;

final class OtlpMasking {

    private OtlpMasking() {}

    @SuppressWarnings("unchecked")
    static Attributes mask(Attributes attributes) {
        AttributesBuilder masked = Attributes.builder();
        attributes.forEach((key, value) -> {
            if (key.getType() == AttributeType.STRING) {
                masked.put((AttributeKey<String>) key, LogMasking.mask((String) value));
            } else if (key.getType() == AttributeType.STRING_ARRAY) {
                masked.put(
                        (AttributeKey<List<String>>) key,
                        ((List<String>) value).stream().map(LogMasking::mask).toList());
            } else {
                masked.put((AttributeKey<Object>) key, value);
            }
        });
        return masked.build();
    }

    @SuppressWarnings("unchecked")
    static Value<?> mask(Value<?> value) {
        if (value == null) {
            return null;
        }
        return switch (value.getType()) {
            case STRING -> Value.of(LogMasking.mask((String) value.getValue()));
            case ARRAY ->
                Value.of(((List<Value<?>>) value.getValue())
                        .stream().<Value<?>>map(OtlpMasking::mask).toList());
            case KEY_VALUE_LIST ->
                Value.of(((List<KeyValue>) value.getValue())
                        .stream()
                                .map(entry -> KeyValue.of(entry.getKey(), mask(entry.getValue())))
                                .toArray(KeyValue[]::new));
            default -> value;
        };
    }
}
