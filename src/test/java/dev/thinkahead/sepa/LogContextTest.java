// ABOUTME: Unit tests for the file id log context used by pollers and processing workers.
// ABOUTME: The file id is set for a block and removed when the block ends.
package dev.thinkahead.sepa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class LogContextTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void scopedFileIdIsRemovedWhenTheScopeCloses() {
        UUID fileId = UUID.randomUUID();

        try (var ignored = LogContext.scopedToFile(fileId)) {
            assertThat(MDC.get(LogContext.FILE_ID)).isEqualTo(fileId.toString());
        }

        assertThat(MDC.get(LogContext.FILE_ID)).isNull();
    }
}
