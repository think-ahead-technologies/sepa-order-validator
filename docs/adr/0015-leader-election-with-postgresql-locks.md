# 15. Leader election with PostgreSQL locks

Date: 2026-10-31

## Status

Accepted

## Context

The validator runs as several instances on OpenShift for availability. Every instance polls the customer SFTP inbound directory. Without coordination, two instances can pick up the same file in the same poll cycle and process it twice — a test with two instances reproduced exactly that (one file, two pain.002 reports). For a bank this means duplicate payment processing.

Options considered:

- **OpenShift Lease API** (Spring Cloud Kubernetes leader election): platform-native, but needs RBAC on leases, couples the application to the cluster and cannot be tested without one.
- **ShedLock**: a database lock per scheduled run; simple, but any instance may run each cycle and there is no stable "I am the leader" state for other singleton tasks.
- **Spring Integration `LockRegistryLeaderInitiator` on `JdbcLockRegistry`**: true leader election over the PostgreSQL database we already operate, with Spring Integration already on the classpath.

## Decision

- Leader election uses Spring Integration's `LockRegistryLeaderInitiator` with a `JdbcLockRegistry` on PostgreSQL.
- The lock lives in the Flyway-managed `INT_LOCK` table, region `sepa`, role `sftp-intake`.
- Only the leader polls customer channels; followers skip their poll cycles.
- Timing is configurable (`sepa.leader-election.*`): lock time-to-live 10 s, heartbeat 1 s. The heartbeat must be at most half the time-to-live; startup fails otherwise.
- Leadership changes are logged at INFO and published as Spring Integration `OnGrantedEvent` / `OnRevokedEvent`.

## Consequences

- No new infrastructure; leader election is platform-independent and tested end to end with two real application instances (exactly-once processing, failover on shutdown, takeover after a crashed leader's lock expires).
- Graceful shutdown releases the lock immediately; after a crash, failover takes up to the lock time-to-live.
- A leader that stalls longer than the time-to-live (long GC pause, network partition to the database) can briefly overlap with a new leader. Leader election alone is therefore not a correctness guarantee: duplicate-file detection by SHA-256 and `MsgId` (planned, see design doc) is the safety net.
- Lock expiry is computed from each instance's clock (UTC); instance clocks must be NTP-synchronised.
- Further singleton tasks (e.g. EBICS polling, housekeeping) can reuse the same mechanism with their own role.
