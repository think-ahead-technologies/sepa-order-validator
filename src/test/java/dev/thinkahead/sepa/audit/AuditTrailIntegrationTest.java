// ABOUTME: Integration tests for the audit module alone against a real PostgreSQL container.
// ABOUTME: Entries are written only inside the caller's transaction, as JSON, and the table is append-only.
package dev.thinkahead.sepa.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.thinkahead.sepa.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class AuditTrailIntegrationTest {

    @Autowired
    AuditTrail auditTrail;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    JdbcClient jdbc;

    record FileArchived(UUID fileId, String archive) {
    }

    @Test
    void recordsAnEntryWithTheEventAsJsonInTheCallersTransaction() {
        UUID fileId = UUID.randomUUID();

        transaction.executeWithoutResult(status -> auditTrail.record(fileId, new FileArchived(fileId, "A-1")));

        assertThat(eventTypes(fileId)).containsExactly("FileArchived");
        assertThat(jdbc.sql("select payload ->> 'archive' from audit_event where file_id = ?").param(fileId)
                .query(String.class).single()).isEqualTo("A-1");
    }

    @Test
    void leavesNoEntryWhenTheCallersTransactionRollsBack() {
        UUID fileId = UUID.randomUUID();

        transaction.executeWithoutResult(status -> {
            auditTrail.record(fileId, new FileArchived(fileId, "A-2"));
            status.setRollbackOnly();
        });

        assertThat(eventTypes(fileId)).isEmpty();
    }

    @Test
    void refusesToRecordOutsideATransaction() {
        UUID fileId = UUID.randomUUID();

        assertThatThrownBy(() -> auditTrail.record(fileId, new FileArchived(fileId, "A-3")))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(eventTypes(fileId)).isEmpty();
    }

    @Test
    void auditTrailIsAppendOnly() {
        UUID fileId = UUID.randomUUID();
        transaction.executeWithoutResult(status -> auditTrail.record(fileId, new FileArchived(fileId, "A-4")));

        assertThatThrownBy(() -> jdbc.sql("update audit_event set event_type = 'tampered' where file_id = ?")
                .param(fileId).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("delete from audit_event where file_id = ?").param(fileId).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("truncate audit_event").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThat(eventTypes(fileId)).hasSize(1);
    }

    private List<String> eventTypes(UUID fileId) {
        return jdbc.sql("select event_type from audit_event where file_id = ?").param(fileId)
                .query(String.class).list();
    }
}
