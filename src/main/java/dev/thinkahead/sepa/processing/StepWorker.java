// ABOUTME: Runs one processing step: a poller claims due work items while a worker is free and hands them over.
// ABOUTME: Failed attempts are retried with backoff; after the last one the step's fallback or a failure is recorded.
package dev.thinkahead.sepa.processing;

import dev.thinkahead.sepa.LogContext;
import dev.thinkahead.sepa.processing.WorkItemRepository.ClaimedItem;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

final class StepWorker {

    private static final Logger LOG = LoggerFactory.getLogger(StepWorker.class);
    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(10);

    private final ProcessingStep step;
    private final StepSettings settings;
    private final WorkItemRepository workItems;
    private final TransactionTemplate transactions;
    private final Semaphore freeWorkers;

    private volatile boolean running;
    private Thread poller;
    private ExecutorService workers;

    StepWorker(ProcessingStep step, StepSettings settings, WorkItemRepository workItems,
            TransactionTemplate transactions) {
        this.step = step;
        this.settings = settings;
        this.workItems = workItems;
        this.transactions = transactions;
        this.freeWorkers = new Semaphore(settings.workers());
    }

    void start() {
        String name = "step-" + step.step().name().toLowerCase();
        running = true;
        workers = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(name + "-", 1).factory());
        poller = Thread.ofVirtual().name(name + "-poller").start(this::poll);
    }

    /**
     * Stops claiming and waits briefly for running items; an item still running keeps its lease and is taken over
     * by another instance once the lease expires.
     */
    void stop() {
        running = false;
        poller.interrupt();
        workers.shutdown();
        try {
            if (!workers.awaitTermination(SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workers.shutdownNow();
        }
    }

    boolean isRunning() {
        return running;
    }

    private void poll() {
        while (running) {
            try {
                freeWorkers.acquire();
                Optional<ClaimedItem> item = claimOrNothing();
                if (item.isEmpty()) {
                    freeWorkers.release();
                    Thread.sleep(settings.pollInterval());
                    continue;
                }
                workers.execute(() -> {
                    try {
                        process(item.get());
                    } finally {
                        freeWorkers.release();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private Optional<ClaimedItem> claimOrNothing() {
        try {
            return workItems.claim(step.step(), settings.lease());
        } catch (RuntimeException e) {
            LOG.warn("Could not claim {} work items, trying again in {}", step.step(), settings.pollInterval(), e);
            return Optional.empty();
        }
    }

    void process(ClaimedItem item) {
        try (var ignored = LogContext.scopedToFile(item.fileId())) {
            if (item.attempt() > settings.maxAttempts()) {
                // Earlier attempts ended without recording anything, e.g. their worker crashed: do not try again.
                giveUp(item, "attempts exhausted without a recorded outcome", null);
                return;
            }
            try {
                Completion completion = performWithinHardStop(item);
                completeWith(item, completion);
            } catch (RuntimeException e) {
                handleFailure(item, e);
            }
        }
    }

    /**
     * Runs the step's work in its own thread and interrupts it at the hard stop, so a hanging attempt (e.g. on an
     * unresponsive SFTP server) ends before its lease expires and another worker could start.
     */
    private Completion performWithinHardStop(ClaimedItem item) {
        Instant deadline = Instant.now().plus(settings.timeout());
        var work = new FutureTask<>(() -> {
            try (var ignored = LogContext.scopedToFile(item.fileId())) {
                return step.perform(item.fileId(), deadline);
            }
        });
        Thread.ofVirtual().name(Thread.currentThread().getName() + "-work").start(work);
        try {
            return work.get(settings.hardStopAfter().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            work.cancel(true);
            throw new StepTimedOut(step.step(), settings.timeout());
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            throw new IllegalStateException(step.step() + " failed", e.getCause());
        } catch (InterruptedException e) {
            work.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(step.step() + " was interrupted by shutdown", e);
        }
    }

    private void completeWith(ClaimedItem item, Completion completion) {
        Boolean completed = transactions.execute(status -> {
            if (!workItems.complete(item)) {
                status.setRollbackOnly();
                return false;
            }
            completion.record();
            return true;
        });
        if (!Boolean.TRUE.equals(completed)) {
            LOG.warn("{} lost its lease before completing; another worker has taken the item over", step.step());
        }
    }

    private void handleFailure(ClaimedItem item, RuntimeException e) {
        String error = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
        if (item.attempt() >= settings.maxAttempts()) {
            giveUp(item, error, e);
            return;
        }
        Duration backoff = settings.backoffAfter(item.attempt());
        LOG.warn("{} failed (attempt {} of {}), retrying in {}", step.step(), item.attempt(),
                settings.maxAttempts(), backoff, e);
        if (!workItems.retryLater(item, backoff, error)) {
            LOG.warn("{} lost its lease before recording the failure; another worker has taken the item over",
                    step.step());
        }
    }

    /**
     * Records the step's fallback outcome if it has one, so the file still moves on; otherwise fails the item.
     */
    private void giveUp(ClaimedItem item, String error, RuntimeException cause) {
        Optional<Completion> fallback = step.whenAttemptsExhausted(item.fileId());
        if (fallback.isPresent()) {
            LOG.error("{} gave up after {} attempts; recording its fallback outcome", step.step(), item.attempt(),
                    cause);
            try {
                completeWith(item, fallback.get());
                return;
            } catch (RuntimeException e) {
                LOG.error("{} could not record its fallback outcome", step.step(), e);
            }
        } else {
            LOG.error("{} failed for good after {} attempts; the work item needs a manual re-drive", step.step(),
                    item.attempt(), cause);
        }
        if (!workItems.fail(item, error)) {
            LOG.warn("{} lost its lease before recording the failure; another worker has taken the item over",
                    step.step());
        }
    }
}
