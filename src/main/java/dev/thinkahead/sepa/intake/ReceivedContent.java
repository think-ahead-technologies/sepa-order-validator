// ABOUTME: Size, SHA-256 and bytes of a received payment file; bytes are absent when the file exceeds the maximum size.
// ABOUTME: Oversized files are hashed while streaming, so their size never determines memory use.
package dev.thinkahead.sepa.intake;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

record ReceivedContent(long size, String sha256, byte[] content) {

    static ReceivedContent of(byte[] content) {
        return new ReceivedContent(
                content.length, HexFormat.of().formatHex(newDigest().digest(content)), content);
    }

    static ReceivedContent oversized(InputStream in) {
        MessageDigest digest = newDigest();
        try (var hashing = new DigestInputStream(in, digest)) {
            long size = hashing.transferTo(OutputStream.nullOutputStream());
            return new ReceivedContent(size, HexFormat.of().formatHex(digest.digest()), null);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot hash oversized payment file", e);
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
