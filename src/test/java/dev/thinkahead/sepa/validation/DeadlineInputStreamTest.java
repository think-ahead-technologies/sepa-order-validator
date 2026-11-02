// ABOUTME: Unit tests for DeadlineInputStream, which stops a validation reading its input once the deadline passed.
// ABOUTME: Reads succeed before the deadline and fail with ValidationDeadlineExceeded after it.
package dev.thinkahead.sepa.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeadlineInputStreamTest {

    private final byte[] content = "<Document/>".getBytes();

    @Test
    void readsNormallyBeforeTheDeadline() throws Exception {
        var in = new DeadlineInputStream(
                new ByteArrayInputStream(content), Instant.now().plusSeconds(60));

        assertThat(in.readAllBytes()).isEqualTo(content);
    }

    @Test
    void failsEveryReadOnceTheDeadlineHasPassed() {
        var in = new DeadlineInputStream(
                new ByteArrayInputStream(content), Instant.now().minusSeconds(1));

        assertThatExceptionOfType(ValidationDeadlineExceeded.class).isThrownBy(in::read);
        assertThatExceptionOfType(ValidationDeadlineExceeded.class).isThrownBy(() -> in.read(new byte[4], 0, 4));
    }
}
