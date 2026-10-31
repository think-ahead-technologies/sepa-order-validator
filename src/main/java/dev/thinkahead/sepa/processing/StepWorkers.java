// ABOUTME: Starts one StepWorker for every ProcessingStep in the application and stops them before shutdown.
// ABOUTME: Runs on every instance: workers compete for items through the database, not through leader election.
package dev.thinkahead.sepa.processing;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class StepWorkers implements SmartLifecycle {

    private final List<StepWorker> workers;

    StepWorkers(List<ProcessingStep> steps, ProcessingProperties properties, WorkItemRepository workItems,
            TransactionTemplate transactions) {
        Map<Step, ProcessingStep> byStep = steps.stream().collect(Collectors.toMap(ProcessingStep::step,
                Function.identity(), (first, second) -> {
                    throw new IllegalStateException("Two implementations of step " + first.step() + ": "
                            + first.getClass().getName() + " and " + second.getClass().getName());
                }));
        this.workers = byStep.values().stream()
                .map(step -> new StepWorker(step, properties.settings(step.step()), workItems, transactions))
                .toList();
    }

    @Override
    public void start() {
        workers.forEach(StepWorker::start);
    }

    @Override
    public void stop() {
        workers.forEach(StepWorker::stop);
    }

    @Override
    public boolean isRunning() {
        return workers.stream().anyMatch(StepWorker::isRunning);
    }
}
