// ABOUTME: Stores exactly one pain.002 status report per payment file, for delivery, redelivery and audit.
// ABOUTME: Uses plain SQL via JdbcClient against the Flyway-managed status_report table.
package dev.thinkahead.sepa.reporting;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class StatusReportRepository {

    private final JdbcClient jdbc;

    StatusReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean existsForFile(UUID fileId) {
        return jdbc.sql("select count(*) from status_report where file_id = ?")
                        .param(fileId)
                        .query(Integer.class)
                        .single()
                > 0;
    }

    /**
     * @return false if a report for the file already exists; the existing report is left untouched
     */
    boolean insertIfAbsent(UUID fileId, StatusReport report, byte[] content) {
        return jdbc.sql("""
                        insert into status_report (message_id, file_id, group_status, content, created_at)
                        values (:messageId, :fileId, :groupStatus, :content, :createdAt)
                        on conflict (file_id) do nothing""")
                        .param("messageId", report.messageId())
                        .param("fileId", fileId)
                        .param("groupStatus", report.groupStatus().name())
                        .param("content", content)
                        .param("createdAt", report.creationDateTime())
                        .update()
                == 1;
    }

    StoredStatusReport findByFile(UUID fileId) {
        return jdbc.sql("select message_id, group_status, content from status_report where file_id = ?")
                .param(fileId)
                .query((rs, rowNum) -> new StoredStatusReport(
                        rs.getString("message_id"),
                        GroupStatus.valueOf(rs.getString("group_status")),
                        rs.getBytes("content")))
                .single();
    }

    void markDelivered(UUID fileId) {
        jdbc.sql("update status_report set delivered_at = now() where file_id = ?")
                .param(fileId)
                .update();
    }

    record StoredStatusReport(String messageId, GroupStatus groupStatus, byte[] content) {}
}
