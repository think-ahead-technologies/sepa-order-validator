// ABOUTME: Priority of a file's work items: workers claim due HIGH items before due NORMAL ones of the same step.
// ABOUTME: Set when a file's first step is scheduled; later steps of the file keep it.
package dev.thinkahead.sepa.processing;

public enum Priority {
    NORMAL(0),
    HIGH(1);

    private final int value;

    Priority(int value) {
        this.value = value;
    }

    /**
     * The value stored in work_item.priority; higher values are claimed first.
     */
    public int value() {
        return value;
    }
}
