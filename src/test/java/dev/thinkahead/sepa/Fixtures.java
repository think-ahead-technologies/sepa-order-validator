// ABOUTME: Loads real pain.001 sample files from test resources for use across all test levels.
// ABOUTME: Tests derive invalid variants from the valid sample by targeted text replacement.
package dev.thinkahead.sepa;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class Fixtures {

    public static final String VALID_PAIN_001 = load("/fixtures/valid-pain.001.001.09.xml");
    public static final String SAMPLE_MESSAGE_ID = "MSG-2026-10-31-001";

    private Fixtures() {
    }

    /**
     * A fresh MsgId (33 characters, within ISO Max35Text), so uploads are never rejected as duplicates by accident.
     */
    public static String uniqueMessageId() {
        return "M" + UUID.randomUUID().toString().replace("-", "");
    }

    public static String validPain001(String messageId) {
        return VALID_PAIN_001.replace("<MsgId>" + SAMPLE_MESSAGE_ID + "</MsgId>", "<MsgId>" + messageId + "</MsgId>");
    }

    public static byte[] bytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private static String load(String resource) {
        try (InputStream in = Fixtures.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
