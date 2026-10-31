// ABOUTME: Intake settings (prefix "sepa.intake"): maximum stored file size and the overdue-reply alert.
// ABOUTME: Larger files are recorded with size and hash only and rejected, which bounds memory use per file.
package dev.thinkahead.sepa.intake;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * @param maxFileSize        files above this size are not stored and get rejected
 * @param replyOverdueAfter  a file without a status report to the customer after this long raises an alert
 * @param replyCheckInterval how often the leader checks for overdue replies
 */
@ConfigurationProperties("sepa.intake")
record IntakeProperties(DataSize maxFileSize, Duration replyOverdueAfter, Duration replyCheckInterval) {

    IntakeProperties {
        if (maxFileSize == null || maxFileSize.toBytes() <= 0) {
            throw new IllegalArgumentException("sepa.intake.max-file-size must be positive");
        }
        if (replyOverdueAfter == null || replyCheckInterval == null) {
            throw new IllegalArgumentException(
                    "sepa.intake.reply-overdue-after and sepa.intake.reply-check-interval are required");
        }
    }
}
