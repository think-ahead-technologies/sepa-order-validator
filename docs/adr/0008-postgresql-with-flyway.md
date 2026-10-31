# 8. PostgreSQL with Flyway

Date: 2026-10-31

## Status

Accepted

## Context

The system is stateful: raw files, file lifecycle, identifiers for duplicate detection, violations, mandates and their collection history, audit events. Duplicate checks need unique constraints under concurrent intake.

## Decision

- PostgreSQL as the single database.
- Schema managed exclusively by Flyway migrations, versioned with the code.
- Uniqueness (file hash, `MsgId` per initiating party, `PmtInfId`) enforced by database constraints, not only by application checks.
- Audit events stored in an append-only table (no updates or deletes from the application).

## Consequences

- Race-free duplicate detection.
- Tests run against real PostgreSQL via Testcontainers.
- Raw file storage in the database is simple to start; large volumes may later move to object storage with the hash and reference kept in PostgreSQL.
