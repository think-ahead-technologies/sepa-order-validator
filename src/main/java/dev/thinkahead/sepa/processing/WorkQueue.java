// ABOUTME: Public facade of the processing module: schedules a step for a payment file as a due work item.
// ABOUTME: Joins the caller's transaction, so the work item exists exactly when the caller's result is committed.
package dev.thinkahead.sepa.processing;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkQueue {

    private final WorkItemRepository workItems;

    WorkQueue(WorkItemRepository workItems) {
        this.workItems = workItems;
    }

    /**
     * Makes the step due for the file now, with the priority of the file's earlier steps; a step already scheduled for
     * the file is left unchanged.
     */
    public void enqueue(UUID fileId, Step step) {
        workItems.insertIfAbsent(fileId, step);
    }

    /**
     * Makes the file's first step due now with the given priority, which its later steps keep.
     */
    public void enqueue(UUID fileId, Step step, Priority priority) {
        workItems.insertIfAbsent(fileId, step, priority);
    }
}
