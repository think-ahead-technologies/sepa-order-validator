// ABOUTME: Loads real pain.001 sample files from test resources for use across all test levels.
// ABOUTME: Tests derive invalid variants from the valid sample by targeted text replacement.
package dev.thinkahead.sepa;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class Fixtures {

    public static final String VALID_PAIN_001 = load("/fixtures/valid-pain.001.001.09.xml");

    private Fixtures() {
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
