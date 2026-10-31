// ABOUTME: Integration tests for the audit module alone against a real PostgreSQL container.
// ABOUTME: Module events are recorded as JSON audit entries; the audit table rejects updates, deletes and truncation.
package dev.thinkahead.sepa.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.thinkahead.sepa.TestcontainersConfiguration;
import dev.thinkahead.sepa.intake.Channel;
import dev.thinkahead.sepa.intake.PaymentFileReceived;
import dev.thinkahead.sepa.reporting.GroupStatus;
import dev.thinkahead.sepa.reporting.StatusReportDelivered;
import dev.thinkahead.sepa.validation.PaymentFileValidated;
import dev.thinkahead.sepa.validation.Violation;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.transaction.support.TransactionTemplate;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class AuditLogIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    JdbcClient jdbc;

    @Test
    void recordsEveryModuleEventForTheFile() {
        UUID fileId = UUID.randomUUID();

        transaction.executeWithoutResult(status -> {
            publisher.publishEvent(new PaymentFileReceived(fileId, Channel.SFTP, "sftp:audit", "audit.xml", "ab12"));
            publisher.publishEvent(new PaymentFileValidated(fileId, "MSG-1", "pain.001.001.09", false,
                    List.of(new Violation("AC03", "PmtInf[1]/CdtTrfTxInf[1]/CdtrAcct/Id/IBAN", "Invalid IBAN"))));
            publisher.publishEvent(new StatusReportDelivered(fileId, "STS-1", GroupStatus.RJCT));
        });

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(eventTypes(fileId)).containsExactlyInAnyOrder(
                "PaymentFileReceived", "PaymentFileValidated", "StatusReportDelivered"));
        assertThat(jdbc.sql("select payload ->> 'originalMessageId' from audit_event "
                        + "where file_id = ? and event_type = 'PaymentFileValidated'")
                .param(fileId).query(String.class).single())
                .isEqualTo("MSG-1");
    }

    @Test
    void auditTrailIsAppendOnly() {
        UUID fileId = UUID.randomUUID();
        transaction.executeWithoutResult(status ->
                publisher.publishEvent(new PaymentFileReceived(fileId, Channel.SFTP, "sftp:audit", "audit.xml", "ab12")));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(eventTypes(fileId)).hasSize(1));

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
