# 17. File processing driven by persisted state with a PostgreSQL work queue

Date: 2026-10-31

## Status

Proposed. Once accepted, it supersedes the event-related parts of ADR-0007: at-least-once module events as the processing chain, scheduled resubmission and republishing on restart.

## Context

ADR-0007 requires a persisted lifecycle per file, resumable processing and transitions recorded together with an audit event. Today the chain is driven by Spring Modulith events instead: `PaymentFileReceived` → validation → `PaymentFileValidated` → reporting → `StatusReportCreated` → delivery → `StatusReportDelivered`. Each step runs only because the previous step's event reached a listener. The `payment_file.status` column records progress, but nothing acts on it.

The validator runs as several instances on OpenShift (ADR-0015). An architecture review on 2026-10-31 found that this event chain behaves poorly in that setting:

- **Running work restarted.** Scheduled resubmission used `resubmitIncompletePublicationsOlderThan`, which resubmits every incomplete publication published before the threshold, including runs still `PROCESSING` on another live instance. Every instance did this on every interval, so a slow step was run again concurrently.
- **Duplicate processing on deploys.** `republish-outstanding-events-on-restart` republished publications that were in progress on other live instances, so every rolling deploy could run steps twice concurrently.
- **Recovery depends on registry configuration.** Since the interim fix of 2026-10-31, only `FAILED` publications are resubmitted (`FailedEventPublications`, at most 4 in flight per instance), a crashed run is marked failed once stale (`spring.modulith.events.staleness.processing`, 5 min), and nothing is republished on restart. This is correct, but recovery takes a fixed staleness delay that must exceed the longest run, and it lives in framework configuration rather than in the file's state.
- **No limit on concurrency.** With virtual threads, `@Async` listeners run without a concurrency limit. Each listener transaction takes a database connection first; validation then holds a full file (up to 100 MB) in memory for its whole duration, and delivery holds its transaction open during SFTP I/O. The connection pool (Hikari default 10) therefore caps memory, but a backlog makes the waiting listeners time out on the pool after 30 s, fail, and come back through resubmission.
- **Audit not in the same transaction.** Audit entries are written by a separate listener (`AuditLog`) with a random id per call. Redelivery creates duplicate entries that the append-only table can never remove, and an audit listener that keeps failing leaves a gap. ADR-0007's "transitions persisted transactionally together with an audit event" is therefore not met.
- **Failures only detected.** `UnansweredFileMonitor` raises an alert for files without a reply but cannot move them forward.

The Spring Modulith event publication registry is a transactional outbox: it guarantees that a notification is recorded and eventually delivered. It is not a work queue, so it lacks leases, bounded workers and recovery from crashed workers. We have been using it as one.

## Decision

### Workers act on the persisted state

Every processing step of a file is a row in a PostgreSQL work queue. A fixed set of workers repeatedly asks "which files have a due step?" and does that step. Workers do not react to events. A lost or duplicated notification cannot strand or double a file, because workers keep checking the state.

### The work queue

A Flyway-managed table `work_item`, one row per file and step:

| Column | Meaning |
|---|---|
| `file_id`, `step` | Primary key. The step is one of `VALIDATE`, `REPORT`, `DELIVER`, later `SCREEN` (ADR-0010). |
| `state` | `PENDING`, `DONE`, `FAILED` |
| `attempts`, `next_attempt_at` | Retry with exponential backoff |
| `lease_token`, `locked_until` | Current lease; expired leases can be claimed again |
| `last_error` | Last failure, for operations |

- **Claim.** A worker claims one due item in a short transaction:
  `UPDATE … SET lease_token = gen_random_uuid(), locked_until = now() + :lease, attempts = attempts + 1 WHERE (file_id, step) = (SELECT … WHERE step = :step AND state = 'PENDING' AND next_attempt_at <= now() AND (locked_until IS NULL OR locked_until < now()) ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING …`
- **Complete.** A worker completes an item only with `WHERE file_id = ? AND step = ? AND lease_token = ?`. A worker whose lease expired and was taken over can no longer complete it. The lease token acts as a fencing token, and its rolled-back transaction leaves no trace.
- **Clock.** Lease times use database time (`now()`), so instance clock skew does not matter.

### Each step works outside a transaction and completes in one short transaction

Every step follows the same pattern:

1. **Work** without holding a transaction or connection: read the inputs (the stored file content never changes), compute, and perform any external side effect.
2. **Complete** in one short transaction, fenced by the lease token:
   1. records the step's result, e.g. the validation outcome or the stored pain.002;
   2. moves `payment_file.status` with a conditional update;
   3. writes exactly one audit entry for the transition. The key `(file_id, step)` is unique, so a repeat is impossible;
   4. marks the work item `DONE` and inserts the next step's item `PENDING`.

A crash before completion leaves no database trace, and the item is claimed again after its lease expires. Validation therefore no longer holds a connection while it parses a file.

External side effects (`DELIVER`) must be safe to repeat:

- The pain.002 is written to a staging directory on the same file system as `outbound/` and then renamed into `outbound/`, so a customer never sees a partial or temporary file, whatever their pickup filter.
- SFTP v3 rename fails when the target exists unless the server supports `posix-rename@openssh.com`. A target that already exists with identical content counts as delivered.
- A repeated delivery writes identical bytes under the same name. A customer who already collected the first copy receives the same report again with the same `MsgId`.

### One source of truth for progress

- `work_item` says what happens next; `payment_file.status` is the business status shown to operations and audit. Both are written only in a step's completion transaction, so they cannot diverge.
- Invariant: a file in a non-terminal status has exactly one `PENDING` or leased work item. The unanswered-file monitor also checks this invariant and alerts on a violation.
- `processing` does not write `payment_file.status`. The step implementations do, through the intake facade. This keeps the dependency direction one way: `intake`, `validation` and `reporting` depend on `processing`; `processing` depends on none of them.
- A step's completion names the next step, so `processing` does not need to know the pipeline order.

### Bounded workers, leases and timeouts

- **Workers.** Each step type has its own fixed-size worker pool (`sepa.processing.<step>.workers`). This bounds memory and database connections per stage, and a slow customer SFTP server stalls only delivery.
- **Connection pool.** The pool is sized as the sum of all worker pools plus headroom for intake, leader election, the event publication registry, housekeeping, the actuator health check and the metrics gauges that query on every scrape (ADR-0018). Startup fails if it is smaller.
- **Connection limits across instances.** Instances × pool size must stay below PostgreSQL's `max_connections`, minus what other clients and migrations need.
- **Leases and timeouts.**
  - Every step has a timeout: SFTP connect, read and write timeouts, a JDBC statement timeout, and for `VALIDATE` a deadline on parsing and rule checks.
  - The lease (`sepa.processing.<step>.lease`) must exceed the step timeout, and startup fails otherwise.
  - There is no lease renewal. A step that cannot finish within its lease is treated as failed.
- **Retries.**
  - A failed attempt sets `next_attempt_at` with exponential backoff.
  - Attempts are counted when an item is claimed, so a file that crashes the worker every time (e.g. out of memory) stops after `max-attempts` instead of crash-looping across instances.
  - After `sepa.processing.<step>.max-attempts`, the item becomes `FAILED` and an alert is raised. Overdue replies remain alerted as today.
  - `VALIDATE` never ends without a customer reply: a deterministic validation failure, and exhausting the attempts (including the deadline), yield a technical rejection (`FF01`) and continue with `REPORT`.
  - A `FAILED` `DELIVER` or `REPORT` item is re-driven by operations through a documented procedure that resets the item to `PENDING` and writes an audit entry for the manual intervention. It becomes a management API once management APIs have OAuth2 (design §10).
- **Polling.** One poller per step and instance looks for due items every `sepa.processing.<step>.poll-interval` (default 1 s) while workers are free, and hands claimed items to the step's worker pool; it polls again immediately while items are found. Idle load is one query per step and instance per interval, not one per worker.
- **Housekeeping.** `DONE` items are deleted after a retention period; the audit trail keeps the history.

### Modules and Spring Modulith

- **`processing` module.** A new module owns `work_item`, the worker runtime and the public `ProcessingStep` interface.
- **Step modules.** `validation` and `reporting` implement `ProcessingStep` for their steps. Delivery moves behind the same interface, and `intake` keeps the channel I/O behind its facade.
- **Intake.** Intake inserts the first `VALIDATE` item in the same transaction that stores the file.
- **Audit.** `audit` becomes a facade called inside step transactions and no longer listens to module events. This avoids a dependency cycle (`audit → validation → processing → audit`) and gives one audit entry per transition.
- **Modulith events.** Events stay for notifications that are not part of the processing chain, e.g. metrics and future integrations. Completed publications are deleted (`spring.modulith.events.completion-mode: delete`). `republish-outstanding-events-on-restart` is switched off, and `IncompleteEventResubmission` is removed.
- **Event contracts.** Events that remain are contracts across rolling deploys: fields are only added, and readers ignore fields they do not know.
- **Logging.** Workers scope the file id per item (`LogContext.scopedToFile`, ADR-0018). The task decorator that clears the MDC after async listener tasks is no longer needed once no listener processes files.

## Alternatives considered

- **Keep the event chain and harden it.** Configure staleness, limit `@Async` concurrency, make `AuditLog` idempotent, switch off republishing on restart.
  - Cheaper, and parts of it are done anyway as interim fixes (see Consequences).
  - Rejected as the target: processing would still depend on each event arriving, and recovery would still depend on registry configuration rather than on the file's state.
- **Message broker (Kafka, RabbitMQ).**
  - Gives competing consumers and redelivery.
  - Rejected: new infrastructure to operate and to test with Testcontainers, and the broker's state can diverge from the database's file state. Two sources of truth is exactly what we want to remove.
- **Workflow engine (Temporal, Camunda).**
  - Very strong guarantees.
  - Rejected: heavyweight for a linear pipeline of three to four steps, and it brings its own persistence and operations.
- **Database-backed scheduler library (db-scheduler, JobRunr).**
  - Close to this design, with leases and heartbeats on PostgreSQL.
  - Not chosen for now: our steps need the file status, the step result and the audit entry in the same transaction as completing the item. A library-owned task table makes that harder and duplicates our state.
  - The custom part is small, a claim query and a worker loop. Review should revisit this option if the custom part grows beyond that.

## Consequences

- **Recovery from crashes.** A file in a non-terminal state always has a due or leased work item. A crashed worker's lease expires and another worker finishes the step, with no restart, staleness configuration or republishing involved.
- **No duplicate effects.**
  - Fencing by lease token makes the database effects of a step happen exactly once.
  - The external effects of delivery happen at least once, in a form that is safe to repeat.
  - The audit trail has exactly one entry per transition.
- **Bounded resources.** Memory and connections are bounded by configuration, not by backlog size.
- **Latency and load.** Up to one poll interval of latency per step, and a small constant query load per worker (an indexed `SKIP LOCKED` query). At our volume, files rather than transactions, this is negligible. PostgreSQL `LISTEN/NOTIFY` can wake workers early if latency ever matters.
- **More code to own.** The worker runtime and its configuration are ours to build and test.
- **Tests.** Following ADR-0012, the following are tested against real PostgreSQL and SFTP containers with two application instances:
  - a worker killed mid-step is taken over after its lease;
  - two workers never both complete an item;
  - a repeated delivery yields identical outbound content, also when the final name already exists;
  - each transition has exactly one audit entry;
  - a validation exceeding its deadline is not run twice concurrently and ends in an `FF01` report;
  - a file in a non-terminal status always has exactly one open work item.
- **Interim fixes before migration.** These ship independently of this ADR.
  - Shipped on 2026-10-31: resubmission of `FAILED` publications only, with at most 4 in flight per instance; staleness for `PROCESSING` (5 min); no republishing on restart.
  - Open: idempotent `AuditLog`, outbound write via staging directory and rename, a concurrency limit on `@Async` listeners.
- **Migration order.**
  1. `DELIVER`: the riskiest step today and the easiest to isolate.
  2. `VALIDATE`.
  3. `REPORT`.

  Each step moves to a work item while the remaining steps still use events. The `processing` module and `work_item` arrive with the first step. During this hybrid phase, an event listener that creates a work item inserts it with `ON CONFLICT DO NOTHING`, because the listener may run more than once.
- **Out of scope.** Storing raw files outside PostgreSQL (claim check in object storage) is a separate decision. It depends on retention periods (design Q9). This ADR does not depend on it.
