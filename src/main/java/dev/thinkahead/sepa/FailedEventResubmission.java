// ABOUTME: Periodically resubmits module events whose listener failed or went stale after a crash (ADR-0007).
// ABOUTME: Safe because every module listener is idempotent; runs on every instance and never touches running work.
package dev.thinkahead.sepa;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Only FAILED publications are resubmitted. A run still in progress, on this or another instance, is left alone until
 * the staleness monitor ({@code spring.modulith.events.staleness.processing}) marks it failed.
 */
@Component
class FailedEventResubmission {

    /**
     * Resubmitted listener runs in flight at once per instance, well below the connection pool size, so a backlog
     * of failures cannot exhaust database connections.
     */
    static final int MAX_IN_FLIGHT = 4;

    private final FailedEventPublications publications;
    private final Duration resubmitAfter;

    FailedEventResubmission(FailedEventPublications publications,
            @Value("${sepa.events.resubmit-after}") Duration resubmitAfter) {
        this.publications = publications;
        this.resubmitAfter = resubmitAfter;
    }

    @Scheduled(fixedDelayString = "${sepa.events.resubmit-interval}",
            initialDelayString = "${sepa.events.resubmit-interval}")
    void resubmit() {
        publications.resubmit(ResubmissionOptions.defaults()
                .withMinAge(resubmitAfter)
                .withMaxInFlight(MAX_IN_FLIGHT));
    }
}
