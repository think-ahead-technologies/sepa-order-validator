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
- Intake is idempotent per channel file: a new file is first claimed by an atomic rename to `processing/<file id>-<name>`; the file is then stored under that id (`ON CONFLICT (id) DO NOTHING`, event published only for a new record) and archived. Every claimed file left in `processing/` is finished by the same steps on the next poll, so a crash between any two steps neither loses nor doubles a file. A customer deliberately uploading the same file again is a new file and is handled by duplicate detection (ADR-0016).
- Intake claims only finished uploads: an inbound `*.xml` file is claimed once two consecutive polls see the same size and modification time, so a customer writing directly under the final name is not read half-way. This delays every file by one poll interval; an upload that stalls longer than a poll interval still looks finished, so customers should upload under a temporary name and rename.
- Intake handles each file in isolation: a file that fails is logged at ERROR and retried on the next poll without blocking the files behind it.
- Module events are delivered at least once (Spring Modulith event publication registry). Every listener is idempotent:
  - validation records an outcome only while the file is `RECEIVED` and otherwise does nothing;
  - reporting creates exactly one report per file (unique `file_id`) in its own transaction, and delivery re-sends the stored bytes, so a repeated delivery never produces a second report or message id.
- Failed event deliveries are resubmitted on a schedule (`sepa.events.resubmit-after`, default 5 min) and on restart, so a transient failure heals without intervention.
- A validation that fails unexpectedly is deterministic and would fail on every retry; the customer receives a technical rejection (`FF01` with the file id as reference) and an ERROR is logged.
- The leader alerts (ERROR) about every file without a status report to the customer after `sepa.intake.reply-overdue-after` (default 10 min), whatever the cause.

## Consequences

- A clear, auditable status for every file at all times.
- Every customer file either gets a status report or raises an alert; none is silently stuck.
- New processing steps must follow the same rule: idempotent, with external side effects (deliveries) separated from the transaction that decides them.
- Operations need log-based alerting on ERROR for this application.
