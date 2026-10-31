// ABOUTME: PostgreSQL persistence for received payment files: raw content, size, SHA-256 hash and lifecycle status.
// ABOUTME: Uses plain SQL via JdbcClient against the Flyway-managed payment_file table.
package dev.thinkahead.sepa.intake;

import java.sql.Types;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PaymentFileRepository {

    private final JdbcClient jdbc;

    PaymentFileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return false if a file with this id already exists; the existing record is left untouched
     */
    boolean insertIfAbsent(UUID id, Channel channel, String submitter, String fileName, ReceivedContent content,
            boolean instantOrder) {
        return insertIfAbsent(id, channel, submitter, fileName, content, instantOrder, null, null);
    }

    /**
     * Stores a message split from a container, linked to the container and its position in it.
     *
     * @return false if a file with this id already exists; the existing record is left untouched
     */
    boolean insertIfAbsent(UUID id, Channel channel, String submitter, String fileName, ReceivedContent content,
            boolean instantOrder, UUID containerFileId, Integer containerIndex) {
        return jdbc.sql("""
                        insert into payment_file
                            (id, channel, submitter, file_name, size_bytes, sha256, content, status,
                             instant_order, container_file_id, container_index)
                        values (:id, :channel, :submitter, :fileName, :size, :sha256, :content, :status,
                                :instantOrder, :containerFileId, :containerIndex)
                        on conflict (id) do nothing""")
                .param("id", id)
                .param("channel", channel.name())
                .param("submitter", submitter)
                .param("fileName", fileName)
                .param("size", content.size())
                .param("sha256", content.sha256())
                .param("content", new SqlParameterValue(Types.BINARY, content.content()))
                .param("status", FileStatus.RECEIVED.name())
                .param("instantOrder", instantOrder)
                .param("containerFileId", new SqlParameterValue(Types.OTHER, containerFileId))
                .param("containerIndex", new SqlParameterValue(Types.INTEGER, containerIndex))
                .update() == 1;
    }

    /**
     * @return the stored bytes, or empty if the file exceeded the maximum size and only its hash was kept
     */
    Optional<byte[]> content(UUID id) {
        List<byte[]> rows = jdbc.sql("select content from payment_file where id = ?").param(id)
                .query((rs, rowNum) -> rs.getBytes("content"))
                .list();
        if (rows.isEmpty()) {
            throw new IllegalStateException("Payment file " + id + " not found");
        }
        return Optional.ofNullable(rows.getFirst());
    }

    Submission submission(UUID id) {
        return jdbc.sql("""
                        select instant_order, container_file_id is not null as from_container
                        from payment_file where id = ?""")
                .param(id)
                .query((rs, rowNum) -> new Submission(rs.getBoolean("instant_order"), rs.getBoolean("from_container")))
                .single();
    }

    String submitter(UUID id) {
        return jdbc.sql("select submitter from payment_file where id = ?").param(id).query(String.class).single();
    }

    StoredPaymentFile find(UUID id) {
        return jdbc.sql("select id, channel, file_name from payment_file where id = ?")
                .param(id)
                .query((rs, rowNum) -> new StoredPaymentFile(
                        rs.getObject("id", UUID.class),
                        Channel.valueOf(rs.getString("channel")),
                        rs.getString("file_name")))
                .single();
    }

    void markReplied(UUID id) {
        jdbc.sql("update payment_file set replied_at = now() where id = ?").param(id).update();
    }

    List<UUID> unansweredSince(Duration age, int limit) {
        return jdbc.sql("""
                        select id from payment_file
                        where replied_at is null and status <> 'SPLIT'
                          and received_at < now() - make_interval(secs => ?)
                        order by received_at
                        limit ?""")
                .params(age.toSeconds(), limit)
                .query(UUID.class)
                .list();
    }

    long countUnansweredSince(Duration age) {
        return jdbc.sql("""
                        select count(*) from payment_file
                        where replied_at is null and status <> 'SPLIT'
                          and received_at < now() - make_interval(secs => ?)""")
                .param(age.toSeconds())
                .query(Long.class)
                .single();
    }

    FileStatus status(UUID id) {
        return jdbc.sql("select status from payment_file where id = ?").param(id)
                .query((rs, rowNum) -> FileStatus.valueOf(rs.getString("status")))
                .single();
    }

    /**
     * Moves the file from one status to the next.
     *
     * @return false if the file was no longer in the expected status
     */
    boolean updateStatus(UUID id, FileStatus expected, FileStatus next) {
        return jdbc.sql("""
                        update payment_file set status = ?, status_changed_at = now()
                        where id = ? and status = ?""")
                .params(next.name(), id, expected.name())
                .update() == 1;
    }
}
