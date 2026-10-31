// ABOUTME: Puts the payment file id into the logging MDC, so every log line about a file carries its "fileId".
// ABOUTME: Pollers and processing workers scope it to the block that handles one file (ADR-0018).
package dev.thinkahead.sepa;

import java.util.UUID;
import org.slf4j.MDC;

public final class LogContext {

    public static final String FILE_ID = "fileId";

    private LogContext() {
    }

    /**
     * For a block on a thread that handles several files in turn, e.g. a poller; close it when the block ends.
     */
    public static MDC.MDCCloseable scopedToFile(UUID fileId) {
        return MDC.putCloseable(FILE_ID, fileId.toString());
    }
}
