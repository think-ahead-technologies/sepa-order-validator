// ABOUTME: Integration tests for the PostgreSQL work queue: claiming with leases, fencing, retries and failure.
// ABOUTME: Runs the real claim and completion SQL against a PostgreSQL container, including concurrent claims.
package dev.thinkahead.sepa.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.thinkahead.sepa.TestcontainersConfiguration;
import dev.thinkahead.sepa.processing.WorkItemRepository.ClaimedItem;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class WorkItemRepositoryIntegrationTest {

    private static final Duration LEASE = Duration.ofMinutes(1);

    @Autowired
    WorkItemRepository workItems;

    @Autowired
    WorkQueue workQueue;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void emptyQueue() {
        jdbc.sql("delete from work_item").update();
    }

    @Test
    void enqueuingTheSameStepTwiceCreatesOneItem() {
        UUID fileId = UUID.randomUUID();

        workQueue.enqueue(fileId, Step.DELIVER);
        workQueue.enqueue(fileId, Step.DELIVER);

        assertThat(jdbc.sql("select count(*) from work_item where file_id = ?").param(fileId)
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void aClaimedItemIsLeasedToOneWorkerAndCountsTheAttempt() {
        UUID fileId = UUID.randomUUID();
        workQueue.enqueue(fileId, Step.DELIVER);

        Optional<ClaimedItem> first = workItems.claim(Step.DELIVER, LEASE);
        Optional<ClaimedItem> second = workItems.claim(Step.DELIVER, LEASE);

        assertThat(first).hasValueSatisfying(item -> {
            assertThat(item.fileId()).isEqualTo(fileId);
            assertThat(item.attempt()).isEqualTo(1);
        });
        assertThat(second).as("leased item is not handed out again").isEmpty();
    }

    @Test
    void anExpiredLeaseIsClaimedAgainAndFencesOutTheWorkerThatLostIt() {
        workQueue.enqueue(UUID.randomUUID(), Step.DELIVER);
        ClaimedItem lost = workItems.claim(Step.DELIVER, Duration.ofMillis(100)).orElseThrow();

        ClaimedItem takenOver = await().atMost(Duration.ofSeconds(5))
                .until(() -> workItems.claim(Step.DELIVER, LEASE), Optional::isPresent).orElseThrow();

        assertThat(takenOver.attempt()).isEqualTo(2);
        assertThat(workItems.complete(lost)).as("stale lease token").isFalse();
        assertThat(workItems.complete(takenOver)).isTrue();
        assertThat(state(takenOver.fileId())).isEqualTo("DONE");
    }

    @Test
    void concurrentWorkersEachClaimDistinctItems() throws Exception {
        List<UUID> fileIds = IntStream.range(0, 40).mapToObj(i -> UUID.randomUUID()).toList();
        fileIds.forEach(fileId -> workQueue.enqueue(fileId, Step.DELIVER));
        Set<UUID> claimed = ConcurrentHashMap.newKeySet();
        List<UUID> claimedTwice = new java.util.concurrent.CopyOnWriteArrayList<>();

        try (var workers = Executors.newFixedThreadPool(8)) {
            for (int worker = 0; worker < 8; worker++) {
                // Bounded, so a claim that hands out leased items again fails the test instead of looping forever.
                workers.submit(() -> {
                    for (int attempt = 0; attempt < fileIds.size(); attempt++) {
                        Optional<ClaimedItem> item = workItems.claim(Step.DELIVER, LEASE);
                        if (item.isEmpty()) {
                            return;
                        }
                        if (!claimed.add(item.get().fileId())) {
                            claimedTwice.add(item.get().fileId());
                        }
                    }
                });
            }
        }

        assertThat(claimed).containsExactlyInAnyOrderElementsOf(fileIds);
        assertThat(claimedTwice).isEmpty();
    }

    @Test
    void aRetriedItemIsDueAgainOnlyAfterItsBackoff() {
        UUID fileId = UUID.randomUUID();
        workQueue.enqueue(fileId, Step.DELIVER);
        ClaimedItem item = workItems.claim(Step.DELIVER, LEASE).orElseThrow();

        assertThat(workItems.retryLater(item, Duration.ofHours(1), "SFTP server unreachable")).isTrue();

        assertThat(workItems.claim(Step.DELIVER, LEASE)).isEmpty();
        assertThat(state(fileId)).isEqualTo("PENDING");
        assertThat(lastError(fileId)).isEqualTo("SFTP server unreachable");
    }

    @Test
    void aFailedItemIsNeverClaimedAgain() {
        UUID fileId = UUID.randomUUID();
        workQueue.enqueue(fileId, Step.DELIVER);
        ClaimedItem item = workItems.claim(Step.DELIVER, LEASE).orElseThrow();

        assertThat(workItems.fail(item, "gave up")).isTrue();

        assertThat(state(fileId)).isEqualTo("FAILED");
        assertThat(lastError(fileId)).isEqualTo("gave up");
        assertThat(jdbc.sql("update work_item set locked_until = null where file_id = ?").param(fileId).update())
                .isEqualTo(1);
        assertThat(workItems.claim(Step.DELIVER, LEASE)).isEmpty();
    }

    @Test
    void aWorkerThatLostItsLeaseCannotRetryOrFailTheItem() {
        workQueue.enqueue(UUID.randomUUID(), Step.DELIVER);
        ClaimedItem lost = workItems.claim(Step.DELIVER, Duration.ofMillis(100)).orElseThrow();
        ClaimedItem current = await().atMost(Duration.ofSeconds(5))
                .until(() -> workItems.claim(Step.DELIVER, LEASE), Optional::isPresent).orElseThrow();

        assertThat(workItems.retryLater(lost, Duration.ZERO, "late")).isFalse();
        assertThat(workItems.fail(lost, "late")).isFalse();
        assertThat(workItems.complete(current)).isTrue();
    }

    private String state(UUID fileId) {
        return jdbc.sql("select state from work_item where file_id = ?").param(fileId).query(String.class).single();
    }

    private String lastError(UUID fileId) {
        return jdbc.sql("select last_error from work_item where file_id = ?").param(fileId)
                .query(String.class).single();
    }
}
