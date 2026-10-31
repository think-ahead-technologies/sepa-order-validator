// ABOUTME: Public facade of the audit module: records a payment file's transition as an append-only audit entry.
// ABOUTME: Runs only inside the transaction that makes the transition, so the entry exists exactly when it happened.
package dev.thinkahead.sepa.audit;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditTrail {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    AuditTrail(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Stores the event under its simple class name, with the event itself as JSON so the trail stays readable as
     * event types evolve.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID fileId, Object event) {
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
