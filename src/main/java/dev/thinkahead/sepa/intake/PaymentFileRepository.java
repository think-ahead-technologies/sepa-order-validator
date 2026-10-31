// ABOUTME: PostgreSQL persistence for received payment files: raw content, SHA-256 hash and lifecycle status.
// ABOUTME: Uses plain SQL via JdbcClient against the Flyway-managed payment_file table.
package dev.thinkahead.sepa.intake;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PaymentFileRepository {

    private final JdbcClient jdbc;

    PaymentFileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(UUID id, Channel channel, String fileName, String sha256, byte[] content) {
        jdbc.sql("""
                        insert into payment_file (id, channel, file_name, sha256, content, status)
                        values (:id, :channel, :fileName, :sha256, :content, :status)""")
                .param("id", id)
                .param("channel", channel.name())
                .param("fileName", fileName)
                .param("sha256", sha256)
                .param("content", content)
                .param("status", FileStatus.RECEIVED.name())
                .update();
    }

    byte[] content(UUID id) {
        return jdbc.sql("select content from payment_file where id = ?").param(id).query(byte[].class).single();
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

    void updateStatus(UUID id, FileStatus status) {
        int updated = jdbc.sql("update payment_file set status = ?, status_changed_at = now() where id = ?")
                .param(status.name())
                .param(id)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Payment file " + id + " not found");
        }
    }
}
