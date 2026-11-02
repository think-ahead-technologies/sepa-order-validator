// ABOUTME: Result of reading a DK XML container: either container-level faults or the messages it holds.
// ABOUTME: Each message is a standalone pain.001 document that is validated and answered on its own (spec
// container-split).
package dev.thinkahead.sepa.validation.container;

import dev.thinkahead.sepa.validation.Violation;
import java.util.List;

public sealed interface ContainerReading {

    /**
     * The container itself is unusable; it is rejected as a whole and none of its messages are processed.
     */
    record Fault(List<Violation> violations) implements ContainerReading {

        public Fault {
            violations = List.copyOf(violations);
        }
    }

    /**
     * The container's messages, in container order, each as a UTF-8 encoded pain.001 document.
     */
    record Split(List<byte[]> messages) implements ContainerReading {

        public Split {
            messages = List.copyOf(messages);
        }
    }
}
