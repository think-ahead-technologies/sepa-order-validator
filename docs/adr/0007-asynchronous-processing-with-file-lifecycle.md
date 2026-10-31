# 7. Asynchronous processing with a persisted file lifecycle

Date: 2026-10-31

## Status

Accepted

## Context

Files arrive via EBICS and SFTP — both are asynchronous channels. Large files take time to validate, and screening hits may wait hours for a human decision. The process must survive restarts.

## Decision

Each file has a persisted lifecycle state:

```
RECEIVED → VALIDATING → REJECTED
                      → SCREENING → ACCEPTED
                                  → ON_HOLD → RELEASED (→ ACCEPTED)
                                            → REJECTED
```

- Intake persists the file and returns immediately; processing continues asynchronously.
- State transitions are persisted transactionally together with an audit event.
- Processing is resumable: on restart, files in non-terminal states are picked up again.
- Intake is idempotent (same file hash → same file record).

## Consequences

- A clear, auditable status for every file at all times.
- Processing steps must be idempotent so they can be retried after a crash.
- Requires a mechanism to pick up pending work (Spring Modulith event publication registry or a polling job).
