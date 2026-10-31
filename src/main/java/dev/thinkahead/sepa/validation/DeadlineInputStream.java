// ABOUTME: Input stream that refuses further reads once a deadline has passed, stopping a validation in progress.
// ABOUTME: Schema validation and parsing read the file through it, so even a long valid file stops on time.
package dev.thinkahead.sepa.validation;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

final class DeadlineInputStream extends FilterInputStream {

    private final Instant deadline;

    DeadlineInputStream(InputStream in, Instant deadline) {
        super(in);
        this.deadline = deadline;
    }

    @Override
    public int read() throws IOException {
        checkDeadline();
        return super.read();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        checkDeadline();
        return super.read(buffer, offset, length);
    }

    private void checkDeadline() {
        if (Instant.now().isAfter(deadline)) {
            throw new ValidationDeadlineExceeded();
        }
    }
}
