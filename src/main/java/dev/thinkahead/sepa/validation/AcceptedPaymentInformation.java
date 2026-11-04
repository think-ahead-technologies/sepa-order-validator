// ABOUTME: Registry of accepted PmtInfIds per submitter, the basis of cross-file duplicate detection (DU02).
// ABOUTME: Race-free: the primary key decides, and ON CONFLICT avoids aborting the caller's transaction.
package dev.thinkahead.sepa.validation;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AcceptedPaymentInformation {

    private final JdbcClient jdbc;

    AcceptedPaymentInformation(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Registers the payment information id as accepted for the submitter.
     *
     * @return false if the submitter already had a file with this payment information id accepted
     */
    boolean register(String submitter, String paymentInformationId, UUID fileId) {
        return jdbc.sql("""
                        insert into accepted_payment_information (submitter, payment_information_id, file_id)
                        values (?, ?, ?)
                        on conflict (submitter, payment_information_id) do nothing""").params(submitter, paymentInformationId, fileId).update() == 1;
    }

    /**
     * Removes every payment information id the file registered, when the file turns out to be rejected after all.
     */
    void unregisterFile(UUID fileId) {
        jdbc.sql("delete from accepted_payment_information where file_id = ?")
                .param(fileId)
                .update();
    }
}
