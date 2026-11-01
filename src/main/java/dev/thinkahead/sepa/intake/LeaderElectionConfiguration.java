// ABOUTME: Wires the PostgreSQL-backed lock registry used to elect the single intake leader (baseline design A5).
// ABOUTME: Locks live in the Flyway-managed INT_LOCK table, region "sepa", and expire after the configured TTL.
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
        return repository;
    }

    @Bean
    JdbcLockRegistry lockRegistry(DefaultLockRepository lockRepository, LeaderElectionProperties properties) {
        return new JdbcLockRegistry(lockRepository, properties.lockTimeToLive());
    }
}
