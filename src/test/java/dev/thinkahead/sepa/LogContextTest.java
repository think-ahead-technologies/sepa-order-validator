// ABOUTME: Unit tests for the file id log context and the task decorator that clears it after every async task.
// ABOUTME: A file id set by one event listener must never appear on log lines of the next task on the same thread.
package dev.thinkahead.sepa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class LogContextTest {

    private final UUID fileId = UUID.randomUUID();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void setsTheFileIdForTheRestOfTheTask() {
        LogContext.setFile(fileId);

        assertThat(MDC.get(LogContext.FILE_ID)).isEqualTo(fileId.toString());
    }

    @Test
    void scopedFileIdIsRemovedWhenTheScopeCloses() {
        try (var ignored = LogContext.scopedToFile(fileId)) {
            assertThat(MDC.get(LogContext.FILE_ID)).isEqualTo(fileId.toString());
        }

        assertThat(MDC.get(LogContext.FILE_ID)).isNull();
    }

    @Test
    void decoratedTaskSeesItsFileIdUntilItEnds() {
        var seen = new AtomicReference<String>();

        new LogContext.ClearingTaskDecorator().decorate(() -> {
            LogContext.setFile(fileId);
            seen.set(MDC.get(LogContext.FILE_ID));
        }).run();

        assertThat(seen.get()).isEqualTo(fileId.toString());
        assertThat(MDC.get(LogContext.FILE_ID)).isNull();
    }

    @Test
    void decoratedTaskClearsTheFileIdEvenWhenItFails() {
        Runnable failing = new LogContext.ClearingTaskDecorator().decorate(() -> {
            LogContext.setFile(fileId);
            throw new IllegalStateException("listener failed");
        });

        assertThatIllegalStateException().isThrownBy(failing::run);
        assertThat(MDC.get(LogContext.FILE_ID)).isNull();
    }
}
