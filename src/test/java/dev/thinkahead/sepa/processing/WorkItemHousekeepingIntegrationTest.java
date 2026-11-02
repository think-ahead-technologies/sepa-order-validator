// ABOUTME: Integration tests for work item housekeeping and the documented re-drive function, on real PostgreSQL.
// ABOUTME: Old DONE items are deleted; re-driving resets an item and writes an audit entry in the same statement.
package dev.thinkahead.sepa.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.thinkahead.sepa.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class WorkItemHousekeepingIntegrationTest {

    @Autowired
    WorkItemHousekeeping housekeeping;

    @Autowired
    JdbcClient jdbc;

    @Test
    void deletesOnlyDoneItemsCompletedBeforeTheRetention() {
        UUID oldDone = insertItem("DONE", "now() - interval '30 days'");
        UUID recentDone = insertItem("DONE", "now() - interval '1 day'");
        UUID oldFailed = insertItem("FAILED", "null");
        UUID pending = insertItem("PENDING", "null");

        housekeeping.purge();

        assertThat(exists(oldDone)).as("old DONE item").isFalse();
        assertThat(exists(recentDone)).as("recent DONE item").isTrue();
        assertThat(exists(oldFailed)).as("FAILED items wait for a re-drive").isTrue();
        assertThat(exists(pending)).isTrue();
    }

    @Test
    void reDrivingAFailedItemMakesItDueWithFreshAttemptsAndAuditsWhoDidItAndWhy() {
        UUID fileId = insertItem("FAILED", "null");
        jdbc.sql("update work_item set attempts = 10, last_error = 'gave up' where file_id = ?")
                .param(fileId)
                .update();

        jdbc.sql("select redrive_work_item(?, 'DELIVER', 'jane.doe', 'customer outbound repaired, INC-4711')")
                .param(fileId)
                .query(String.class)
                .single();

        assertThat(jdbc.sql("""
                        select state || ':' || attempts || ':' || (next_attempt_at <= now()) || ':'
                               || (lease_token is null)
                        from work_item where file_id = ?""").param(fileId).query(String.class).single()).isEqualTo("PENDING:0:true:true");
        assertThat(jdbc.sql("""
                        select payload ->> 'step' || '|' || (payload ->> 'operator') || '|' || (payload ->> 'reason')
                               || '|' || (payload ->> 'previousState')
                        from audit_event where file_id = ? and event_type = 'WorkItemRedriven'""").param(fileId).query(String.class).single())
                .isEqualTo("DELIVER|jane.doe|customer outbound repaired, INC-4711|FAILED");
    }

    @Test
    void reDrivingADoneItemRunsTheStepAgain() {
        UUID fileId = insertItem("DONE", "now()");

        jdbc.sql("select redrive_work_item(?, 'DELIVER', 'jane.doe', 'customer lost the report')")
                .param(fileId)
                .query(String.class)
                .single();

        assertThat(jdbc.sql("select state from work_item where file_id = ?")
                        .param(fileId)
                        .query(String.class)
                        .single())
                .isEqualTo("PENDING");
    }

    @Test
    void refusesToReDriveAnItemThatIsStillPendingOrDoesNotExist() {
        UUID pending = insertItem("PENDING", "null");

        assertThatThrownBy(() -> jdbc.sql("select redrive_work_item(?, 'DELIVER', 'jane.doe', 'why')")
                        .param(pending)
                        .query(String.class)
                        .single())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("PENDING");
        assertThatThrownBy(() -> jdbc.sql("select redrive_work_item(?, 'DELIVER', 'jane.doe', 'why')")
                        .param(UUID.randomUUID())
                        .query(String.class)
                        .single())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("No DELIVER work item");
        assertThatThrownBy(() -> jdbc.sql("select redrive_work_item(?, 'DELIVER', ' ', 'why')")
                        .param(pending)
                        .query(String.class)
                        .single())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("operator and reason");
    }

    private UUID insertItem(String state, String completedAt) {
        UUID fileId = UUID.randomUUID();
        jdbc.sql("insert into work_item (file_id, step, state, completed_at) values (?, 'DELIVER', ?, %s)"
                        .formatted(completedAt))
                .params(fileId, state)
                .update();
        return fileId;
    }

    private boolean exists(UUID fileId) {
        return jdbc.sql("select count(*) from work_item where file_id = ?")
                        .param(fileId)
                        .query(Integer.class)
                        .single()
                == 1;
    }
}
