// ABOUTME: Content of a pain.002 customer payment status report for one payment file.
// ABOUTME: Original message id and name are null when the received file could not be identified.
package dev.thinkahead.sepa.reporting;

import dev.thinkahead.sepa.validation.Violation;
import java.time.OffsetDateTime;
import java.util.List;

record StatusReport(
        String messageId,
        OffsetDateTime creationDateTime,
        String originalMessageId,
        String originalMessageNameId,
        GroupStatus groupStatus,
        List<Violation> reasons) {}
