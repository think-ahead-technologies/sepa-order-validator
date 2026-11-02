// ABOUTME: Registry of accepted message ids per submitter, the basis of duplicate MsgId detection (DU01).
// ABOUTME: Race-free: the primary key decides, and ON CONFLICT avoids aborting the caller's transaction.
package dev.thinkahead.sepa.validation;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AcceptedMessages {

    private final JdbcClient jdbc;

    AcceptedMessages(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Registers the message id as accepted for the submitter.
     *
     * @return false if the submitter already had a file with this message id accepted
     */
    boolean register(String submitter, String messageId, UUID fileId) {
        return jdbc.sql("""
                        insert into accepted_message (submitter, message_id, file_id)
                        values (?, ?, ?)
                        on conflict (submitter, message_id) do nothing""").params(submitter, messageId, fileId).update() == 1;
    }
}
