// ABOUTME: Elects one intake leader across all running instances via a shared lock (spec file-intake).
// ABOUTME: Only the leader polls customer channels, so every received file is processed exactly once.
package dev.thinkahead.sepa.intake;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.integration.leader.Context;
import org.springframework.integration.leader.DefaultCandidate;
import org.springframework.integration.support.leader.LockRegistryLeaderInitiator;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Component;

@Component
class IntakeLeadership implements SmartLifecycle {

    static final String ROLE = "sftp-intake";
    static final String LOCK_REGION = "sepa";

    private static final Logger LOG = LoggerFactory.getLogger(IntakeLeadership.class);

    private final LockRegistryLeaderInitiator initiator;

    IntakeLeadership(LockRegistry<?> locks, LeaderElectionProperties properties, ApplicationEventPublisher events) {
        this.initiator = new LockRegistryLeaderInitiator(locks, new LoggingCandidate(UUID.randomUUID().toString()));
        this.initiator.setHeartBeatMillis(properties.heartbeat().toMillis());
        this.initiator.setApplicationEventPublisher(events);
    }

    boolean isLeader() {
        return initiator.getContext().isLeader();
    }

    @Override
    public void start() {
        initiator.start();
    }

    @Override
    public void stop() {
        initiator.stop();
    }

    @Override
    public boolean isRunning() {
        return initiator.isRunning();
    }

    @Override
    public int getPhase() {
        return initiator.getPhase();
    }

    private static final class LoggingCandidate extends DefaultCandidate {

        LoggingCandidate(String id) {
            super(id, ROLE);
        }

        @Override
        public void onGranted(Context context) {
            LOG.info("Instance {} became leader for {}", getId(), getRole());
        }

        @Override
        public void onRevoked(Context context) {
            LOG.info("Instance {} is no longer leader for {}", getId(), getRole());
        }
    }
}
