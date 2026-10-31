// ABOUTME: Periodically resubmits module events whose listener failed, so a transient failure heals without a restart.
// ABOUTME: Safe because every module listener is idempotent (ADR-0007); runs on every instance.
package dev.thinkahead.sepa;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class IncompleteEventResubmission {

    private final IncompleteEventPublications publications;
    private final Duration resubmitAfter;

    IncompleteEventResubmission(IncompleteEventPublications publications,
            @Value("${sepa.events.resubmit-after}") Duration resubmitAfter) {
        this.publications = publications;
        this.resubmitAfter = resubmitAfter;
    }

    @Scheduled(fixedDelayString = "${sepa.events.resubmit-interval}",
            initialDelayString = "${sepa.events.resubmit-interval}")
    void resubmit() {
        publications.resubmitIncompletePublicationsOlderThan(resubmitAfter);
    }
}
