// ABOUTME: Records every module event of a payment file's journey as an append-only audit entry.
// ABOUTME: Events are stored as JSON so the audit trail stays readable as event types evolve.
package dev.thinkahead.sepa.audit;

import dev.thinkahead.sepa.LogContext;
import dev.thinkahead.sepa.intake.PaymentFileReceived;
import dev.thinkahead.sepa.reporting.StatusReportDelivered;
import dev.thinkahead.sepa.validation.PaymentFileValidated;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class AuditLog {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    AuditLog(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @ApplicationModuleListener
    void on(PaymentFileReceived event) {
        append(event.fileId(), event);
    }

    @ApplicationModuleListener
    void on(PaymentFileValidated event) {
        append(event.fileId(), event);
    }

    @ApplicationModuleListener
    void on(StatusReportDelivered event) {
        append(event.fileId(), event);
    }

    private void append(UUID fileId, Object event) {
        LogContext.setFile(fileId);
        jdbc.sql("""
                        insert into audit_event (id, file_id, event_type, payload)
                        values (:id, :fileId, :eventType, cast(:payload as jsonb))""")
                .param("id", UUID.randomUUID())
                .param("fileId", fileId)
                .param("eventType", event.getClass().getSimpleName())
                .param("payload", json.writeValueAsString(event))
                .update();
    }
}
