// ABOUTME: Stores every generated pain.002 status report for audit and redelivery (ADR-0013).
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

    void insert(UUID fileId, StatusReport report, byte[] content) {
        jdbc.sql("""
                        insert into status_report (message_id, file_id, group_status, content, created_at)
                        values (:messageId, :fileId, :groupStatus, :content, :createdAt)""")
                .param("messageId", report.messageId())
                .param("fileId", fileId)
                .param("groupStatus", report.groupStatus().name())
                .param("content", content)
                .param("createdAt", report.creationDateTime())
                .update();
    }
}
