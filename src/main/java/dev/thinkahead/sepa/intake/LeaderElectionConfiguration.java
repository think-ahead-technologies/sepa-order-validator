// ABOUTME: Wires the PostgreSQL-backed lock registry used to elect the single intake leader (baseline design A5).
// ABOUTME: Locks live in INT_LOCK, region "sepa", expire after the TTL; a lost race inserts nothing instead of failing.
package dev.thinkahead.sepa.intake;

import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.jdbc.lock.DefaultLockRepository;
import org.springframework.integration.jdbc.lock.JdbcLockRegistry;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LeaderElectionProperties.class)
class LeaderElectionConfiguration {

    @Bean
    DefaultLockRepository lockRepository(DataSource dataSource) {
        var repository = new DefaultLockRepository(dataSource);
        repository.setRegion(IntakeLeadership.LOCK_REGION);
        // Standby instances try to take the held leader lock several times a second. Without the hint, every attempt
        // is a unique-constraint violation that PostgreSQL logs as an ERROR; with it, the insert affects no row and
        // the repository reports the lock as not acquired.
        repository.setInsertQuery(repository.getInsertQuery().strip() + " ON CONFLICT DO NOTHING");
        return repository;
    }

    @Bean
    JdbcLockRegistry lockRegistry(DefaultLockRepository lockRepository, LeaderElectionProperties properties) {
        return new JdbcLockRegistry(lockRepository, properties.lockTimeToLive());
    }
}
