// ABOUTME: Stores each payment file's validation outcome, including all violations, for the REPORT step.
// ABOUTME: Violations are kept as JSON in the Flyway-managed validation_result table.
package dev.thinkahead.sepa.validation;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class ValidationOutcomeRepository {

    private static final TypeReference<List<Violation>> VIOLATIONS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    ValidationOutcomeRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void insert(PaymentFileValidated outcome) {
        jdbc.sql("""
                        insert into validation_result
                            (file_id, original_message_id, original_message_name_id, accepted, violations)
                        values (:fileId, :messageId, :messageNameId, :accepted, cast(:violations as jsonb))""")
                .param("fileId", outcome.fileId())
                .param("messageId", outcome.originalMessageId())
                .param("messageNameId", outcome.originalMessageNameId())
                .param("accepted", outcome.accepted())
                .param("violations", json.writeValueAsString(outcome.violations()))
                .update();
    }

    PaymentFileValidated find(UUID fileId) {
        return jdbc.sql("""
                        select original_message_id, original_message_name_id, accepted, violations
                        from validation_result where file_id = ?""")
                .param(fileId)
                .query((rs, rowNum) -> new PaymentFileValidated(
                        fileId,
                        rs.getString("original_message_id"),
                        rs.getString("original_message_name_id"),
                        rs.getBoolean("accepted"),
                        json.readValue(rs.getString("violations"), VIOLATIONS)))
                .single();
    }
}
