// ABOUTME: Unit tests for received file content: size and SHA-256, for stored and for oversized files.
// ABOUTME: An oversized file is hashed while streaming, without keeping its bytes in memory.
package dev.thinkahead.sepa.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReceivedContentTest {

    private static final byte[] CONTENT = "<Document/>".getBytes(StandardCharsets.UTF_8);

    @Test
    void storedContentKeepsBytesSizeAndHash() {
        ReceivedContent received = ReceivedContent.of(CONTENT);

        assertThat(received.content()).isEqualTo(CONTENT);
        assertThat(received.size()).isEqualTo(CONTENT.length);
        assertThat(received.sha256()).isEqualTo(sha256Hex(CONTENT));
    }

    @Test
    void oversizedContentIsHashedWhileStreamingAndNotKept() {
        ReceivedContent received = ReceivedContent.oversized(new ByteArrayInputStream(CONTENT));

        assertThat(received.content()).isNull();
        assertThat(received.size()).isEqualTo(CONTENT.length);
        assertThat(received.sha256()).isEqualTo(ReceivedContent.of(CONTENT).sha256());
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
