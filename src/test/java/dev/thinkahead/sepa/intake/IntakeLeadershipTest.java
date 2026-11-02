// ABOUTME: Unit tests for intake leader election over Spring Integration's in-memory lock registry.
// ABOUTME: Exactly one of several instances leads, and a follower takes over when the leader stops.
package dev.thinkahead.sepa.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.integration.support.locks.DefaultLockRegistry;

class IntakeLeadershipTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final LeaderElectionProperties PROPERTIES =
            new LeaderElectionProperties(Duration.ofSeconds(3), Duration.ofMillis(100));

    private final DefaultLockRegistry locks = new DefaultLockRegistry();
    private final List<IntakeLeadership> instances = new ArrayList<>();

    @AfterEach
    void stopInstances() {
        instances.stream().filter(IntakeLeadership::isRunning).forEach(IntakeLeadership::stop);
    }

    @Test
    void isNotLeaderBeforeStarting() {
        assertThat(newInstance().isLeader()).isFalse();
    }

    @Test
    void exactlyOneOfTwoInstancesLeads() {
        IntakeLeadership first = started();
        IntakeLeadership second = started();

        await().atMost(TIMEOUT).until(() -> first.isLeader() || second.isLeader());
        await().during(Duration.ofSeconds(1))
                .atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(first.isLeader() ^ second.isLeader())
                        .as("exactly one instance leads")
                        .isTrue());
    }

    @Test
    void followerTakesOverWhenLeaderStops() {
        IntakeLeadership leader = started();
        await().atMost(TIMEOUT).until(leader::isLeader);
        IntakeLeadership follower = started();

        leader.stop();

        await().atMost(TIMEOUT).until(follower::isLeader);
        assertThat(leader.isLeader()).isFalse();
    }

    private IntakeLeadership newInstance() {
        var instance = new IntakeLeadership(locks, PROPERTIES, event -> {});
        instances.add(instance);
        return instance;
    }

    private IntakeLeadership started() {
        IntakeLeadership instance = newInstance();
        instance.start();
        return instance;
    }
}
