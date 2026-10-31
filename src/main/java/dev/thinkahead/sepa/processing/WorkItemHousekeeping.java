// ABOUTME: Periodically deletes DONE work items older than the retention; the audit trail keeps the history.
// ABOUTME: FAILED items are kept until they are re-driven. Safe to run on every instance at once.
package dev.thinkahead.sepa.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

@Component
class WorkItemHousekeeping implements SchedulingConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(WorkItemHousekeeping.class);

    private final WorkItemRepository workItems;
    private final ProcessingProperties properties;

    WorkItemHousekeeping(WorkItemRepository workItems, ProcessingProperties properties) {
        this.workItems = workItems;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::purge, properties.housekeepingInterval());
    }

    void purge() {
        int deleted = workItems.deleteDoneBefore(properties.retention());
        if (deleted > 0) {
            LOG.info("Deleted {} DONE work items older than {}", deleted, properties.retention());
        }
    }
}
