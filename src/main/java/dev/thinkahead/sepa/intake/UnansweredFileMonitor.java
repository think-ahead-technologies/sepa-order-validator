// ABOUTME: Alerts (ERROR log and the sepa.intake.files.unanswered gauge) about files whose customer got no reply in time.
// ABOUTME: Catches every cause of a missing reply, from a stuck validation to an unreachable delivery channel.
package dev.thinkahead.sepa.intake;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class UnansweredFileMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(UnansweredFileMonitor.class);
    private static final int MAX_LISTED = 100;
    static final String UNANSWERED_FILES = "sepa.intake.files.unanswered";

    private final PaymentFileRepository repository;
    private final IntakeLeadership leadership;
    private final IntakeProperties properties;

    UnansweredFileMonitor(PaymentFileRepository repository, IntakeLeadership leadership, IntakeProperties properties,
            MeterRegistry meters) {
        this.repository = repository;
        this.leadership = leadership;
        this.properties = properties;
        // Counted in the database on every scrape, so every instance reports the same value, leader or not.
        Gauge.builder(UNANSWERED_FILES, () -> repository.countUnansweredSince(properties.replyOverdueAfter()))
                .description("Payment files without a status report to the customer for longer than "
                        + "sepa.intake.reply-overdue-after")
                .register(meters);
    }

    @Scheduled(fixedDelayString = "${sepa.intake.reply-check-interval}",
            initialDelayString = "${sepa.intake.reply-check-interval}")
    void scheduledCheck() {
        if (leadership.isLeader()) {
            check();
        }
    }

    /**
     * @return ids of files received longer ago than the threshold without a reply, at most {@value #MAX_LISTED}
     */
    List<UUID> check() {
        List<UUID> overdue = repository.unansweredSince(properties.replyOverdueAfter(), MAX_LISTED);
        if (!overdue.isEmpty()) {
            LOG.error("{} payment file(s) without a reply to the customer for more than {}: {}",
                    overdue.size(), properties.replyOverdueAfter(), overdue);
        }
        return overdue;
    }
}
