// ABOUTME: SQL for the work_item table: claim a due item under a lease, then complete, retry or fail it.
// ABOUTME: Every update after the claim is fenced by the lease token, so a worker that lost its lease changes nothing.
package dev.thinkahead.sepa.processing;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class WorkItemRepository {

    private static final int MAX_ERROR_LENGTH = 1_000;

    private final JdbcClient jdbc;

    WorkItemRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Schedules the step with the priority of the file's other items, or NORMAL for its first item.
     */
    void insertIfAbsent(UUID fileId, Step step) {
        jdbc.sql("""
                        insert into work_item (file_id, step, priority)
                        values (?, ?, coalesce((select max(priority) from work_item where file_id = ?), 0))
                        on conflict do nothing""")
                .params(fileId, step.name(), fileId)
                .update();
    }

    void insertIfAbsent(UUID fileId, Step step, Priority priority) {
        jdbc.sql("insert into work_item (file_id, step, priority) values (?, ?, ?) on conflict do nothing")
                .params(fileId, step.name(), priority.value())
                .update();
    }

    /**
     * Leases the longest-due item of the step with the highest priority to the caller; items leased by others are
     * skipped, not waited for.
     * Lease times use the database clock, so clock skew between instances does not matter.
     */
    Optional<ClaimedItem> claim(Step step, Duration lease) {
        return jdbc.sql("""
                        update work_item w
                        set lease_token = gen_random_uuid(),
                            locked_until = now() + make_interval(secs => :leaseSeconds),
                            attempts = attempts + 1
                        where (w.file_id, w.step) = (
                            select file_id, step from work_item
                            where step = :step and state = 'PENDING' and next_attempt_at <= now()
                              and (locked_until is null or locked_until < now())
                            order by priority desc, next_attempt_at
                            for update skip locked
                            limit 1)
                        returning w.file_id, w.lease_token, w.attempts""")
                .param("step", step.name())
                .param("leaseSeconds", lease.toMillis() / 1_000.0)
                .query((rs, rowNum) -> new ClaimedItem(rs.getObject("file_id", UUID.class), step,
                        rs.getObject("lease_token", UUID.class), rs.getInt("attempts")))
                .optional();
    }

    /**
     * @return false if the lease was lost to another worker; nothing is changed then
     */
    boolean complete(ClaimedItem item) {
        return jdbc.sql("""
                        update work_item
                        set state = 'DONE', completed_at = now(), lease_token = null, locked_until = null,
                            last_error = null
                        where file_id = ? and step = ? and lease_token = ? and state = 'PENDING'""")
                .params(item.fileId(), item.step().name(), item.leaseToken())
                .update() == 1;
    }

    /**
     * @return false if the lease was lost to another worker; nothing is changed then
     */
    boolean retryLater(ClaimedItem item, Duration delay, String error) {
        return jdbc.sql("""
                        update work_item
                        set next_attempt_at = now() + make_interval(secs => ?), lease_token = null,
                            locked_until = null, last_error = ?
                        where file_id = ? and step = ? and lease_token = ? and state = 'PENDING'""")
                .params(delay.toMillis() / 1_000.0, truncate(error), item.fileId(), item.step().name(),
                        item.leaseToken())
                .update() == 1;
    }

    /**
     * Gives the item up; only a manual re-drive makes it due again.
     *
     * @return false if the lease was lost to another worker; nothing is changed then
     */
    boolean fail(ClaimedItem item, String error) {
        return jdbc.sql("""
                        update work_item
                        set state = 'FAILED', lease_token = null, locked_until = null, last_error = ?
                        where file_id = ? and step = ? and lease_token = ? and state = 'PENDING'""")
                .params(truncate(error), item.fileId(), item.step().name(), item.leaseToken())
                .update() == 1;
    }

    /**
     * @return number of DONE items completed longer ago than the retention that were deleted
     */
    int deleteDoneBefore(Duration retention) {
        return jdbc.sql("""
                        delete from work_item
                        where state = 'DONE' and completed_at < now() - make_interval(secs => ?)""")
                .param(retention.toSeconds())
                .update();
    }

    private static String truncate(String error) {
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    /**
     * @param attempt number of this attempt, counted when the item was claimed
     */
    record ClaimedItem(UUID fileId, Step step, UUID leaseToken, int attempt) {
    }
}
