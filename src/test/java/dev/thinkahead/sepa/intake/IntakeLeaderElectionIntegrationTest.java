// ABOUTME: Integration tests for intake leader election backed by the PostgreSQL lock table.
// ABOUTME: Verifies the persisted leader lock, its heartbeat renewal and the leadership-granted event.
package dev.thinkahead.sepa.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.thinkahead.sepa.CapturedEvents;
import dev.thinkahead.sepa.TestSupportConfiguration;
import dev.thinkahead.sepa.TestcontainersConfiguration;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.util.UUIDConverter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;

@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
class IntakeLeaderElectionIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String LOCK_KEY =
            UUIDConverter.getUUID(IntakeLeadership.ROLE).toString();

    @Autowired
    IntakeLeadership leadership;

    @Autowired
    CapturedEvents events;

    @Autowired
    JdbcClient jdbc;

    @Test
    void singleInstanceBecomesLeaderAndPublishesGrantedEvent() {
        await().atMost(TIMEOUT).until(leadership::isLeader);

        assertThat(events.ofType(OnGrantedEvent.class))
                .anySatisfy(event -> assertThat(event.getRole()).isEqualTo(IntakeLeadership.ROLE));
    }

    @Test
    void leaderHoldsAnUnexpiredLockInTheDatabase() {
        await().atMost(TIMEOUT).until(leadership::isLeader);

        LocalDateTime expiresAt = lockExpiry();

        assertThat(expiresAt).isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void heartbeatKeepsExtendingTheLock() {
        await().atMost(TIMEOUT).until(leadership::isLeader);
        LocalDateTime firstExpiry = lockExpiry();

        await().atMost(TIMEOUT).until(() -> lockExpiry().isAfter(firstExpiry));
    }

    private LocalDateTime lockExpiry() {
        return jdbc.sql("select expired_after from int_lock where region = ? and lock_key = ?")
                .param(IntakeLeadership.LOCK_REGION)
                .param(LOCK_KEY)
                .query(LocalDateTime.class)
                .single();
    }
}
