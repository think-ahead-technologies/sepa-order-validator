// ABOUTME: Puts the payment file id into the logging MDC, so every log line about a file carries its "fileId".
// ABOUTME: Async tasks set it for their whole run; ClearingTaskDecorator clears it after each task (ADR-0018).
package dev.thinkahead.sepa;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

public final class LogContext {

    public static final String FILE_ID = "fileId";

    private LogContext() {
    }

    /**
     * For the rest of the current async task, typically a module event listener. It stays set after the listener
     * returns or fails, so failures logged by the framework afterwards still carry the file id.
     */
    public static void setFile(UUID fileId) {
        MDC.put(FILE_ID, fileId.toString());
    }

    /**
     * For a block on a thread that handles several files in turn, e.g. a poller; close it when the block ends.
     */
    public static MDC.MDCCloseable scopedToFile(UUID fileId) {
        return MDC.putCloseable(FILE_ID, fileId.toString());
    }

    /**
     * Applied by Spring Boot to the application task executor: clears the MDC once a task has finished, so no log
     * context leaks into the next task when threads are reused.
     */
    public static final class ClearingTaskDecorator implements TaskDecorator {

        @Override
        public Runnable decorate(Runnable task) {
            return () -> {
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        }
    }
}
