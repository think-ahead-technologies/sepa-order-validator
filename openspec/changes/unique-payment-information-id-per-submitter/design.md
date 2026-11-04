## Context

Cross-file duplicate detection exists for `MsgId` (baseline design A7). `AcceptedMessages.register` inserts `(submitter, message_id)` into `accepted_message` with `ON CONFLICT DO NOTHING`, and the primary key settles races. `PaymentFileValidation.record` calls it inside the `Completion`, which the step worker runs in one short transaction together with the file status, the outcome, the `REPORT` work item and the audit entry. Rejected files never reach the registration step.

`PmtInfId` uniqueness inside a file is a stateful content rule (`UniquePaymentInformationIdRule`). The parser reads each `PaymentInformation` with its `paymentInformationId()` and `path()`, but `ValidationResult` keeps only `MsgId`, message name and violations. The ids are lost by the time `record` runs.

Requirements: see the delta spec `specs/file-validation/spec.md`.

## Goals / Non-Goals

**Goals:**
- Register a file's `PmtInfId` values and its `MsgId` atomically: both are registered or neither is.
- Settle races in the database, as for `MsgId`, without deadlocks between concurrent acceptances.

**Non-Goals:**
- `DU04` (End-to-End id window). It stays on the roadmap.
- An expiry window. It waits for Q12, for both tables together.
- Backfilling ids of files accepted before the migration.

## Decisions

**D1. A table of its own, `accepted_payment_information (submitter, payment_information_id, file_id, accepted_at)`, with primary key `(submitter, payment_information_id)`.** This mirrors `accepted_message`, so the same `ON CONFLICT DO NOTHING` pattern and the same future retention job (Q12) apply. `payment_information_id` is `VARCHAR(35)`, the ISO `Max35Text`.
- *Rejected: a single table `accepted_id (submitter, kind, id)` for both.* Fewer tables, but the two kinds are never queried together, and a `kind` discriminator weakens the schema for no gain.
- *Rejected: a lookup against stored validation outcomes or raw files.* That needs a JSON or XML search at acceptance time, has no unique constraint to settle races, and gets slower as volume grows.

**D2. `ValidationResult` carries the ids and their locations (`PaymentInformationReference(id, path)`), collected by the parser during the rule pass.** `record` needs them after validation, and the result is the only thing that crosses from `perform` (no transaction) to the `Completion`. Results without parsed content (format, schema, timeout, technical, too large) carry an empty list.
- *Rejected: parsing the file again inside the `Completion`.* That puts parsing inside the short transaction the step worker relies on (baseline design on the work queue), and it duplicates work.

**D3. Order inside `record`: register `MsgId` first. On conflict, report `DU01` only. Otherwise register the `PmtInfId` values. On any conflict, delete this file's rows from both tables and report `DU02` for each conflicting block.** The undo is a `DELETE ... WHERE file_id = ?` in the same transaction. It is safe because only this transaction can have inserted rows with this file id.
- *Rejected: checking with `SELECT` first and inserting afterwards.* The check and the insert race. Two concurrent files would both pass the check.
- *Rejected: a savepoint around the inserts, rolled back on conflict.* That works, but it needs `TransactionTemplate` savepoint handling inside a `Completion`, which today is plain JDBC in the caller's transaction. The explicit delete is easier to read and to test.
- *Rejected: reporting `DU02` next to `DU01`.* For a resubmitted identical file every block would repeat as `DU02`, which buries the one useful message, and the existing scenario says the report lists only `DU01`.

**D4. Insert the `PmtInfId` values in sorted order, one statement per id.** Two transactions that insert overlapping key sets in different orders can deadlock on the unique index, because `ON CONFLICT` waits for the other uncommitted insert. Sorting gives every transaction the same lock order. A file with a within-file duplicate never gets here, because the content rule has already rejected it.
- *Rejected: one multi-row `INSERT ... VALUES (...), (...) ON CONFLICT DO NOTHING RETURNING`.* PostgreSQL does not guarantee the order in which a multi-row insert takes its locks, so it does not prevent the deadlock. Batching can come later if profiling demands it.

**D5. No backfill.** Files accepted before the migration have no registered `PmtInfId` values. Backfilling would mean parsing stored raw files inside a Flyway Java migration, which ties the migration to the parser code of that moment and runs a full parse of every historic file at deploy time.
- *Rejected: a Flyway Java migration that parses stored raw files.* Reasons above.
- *Rejected: a one-off backfill job outside Flyway.* That is possible later if Compliance asks for it, and it is independent of this change.

**D6. Logging.** `record` already logs only reason codes. The `PmtInfId` appears only in the violation message that goes to the customer's report, like the `MsgId` in `DU01` (open decision D3 covers whether report texts may quote file values).

## Risks / Trade-offs

- [Ids of files accepted before go-live are not checked] → Documented in the proposal impact. The gap closes as customers rotate their ids. A one-off backfill stays possible.
- [One insert per payment information block inside the acceptance transaction] → Files typically have a few blocks. A file with thousands of blocks makes the transaction longer but bounded. The integration test measures a large realistic file.
- [The table grows without bound until Q12 decides a window] → Same as `accepted_message`. It is listed under Q9/Q12.
- [Deadlock despite sorting, e.g. a `MsgId` and a `PmtInfId` conflict crossing between two files] → The `MsgId` insert always comes first and the `PmtInfId` inserts are sorted, so every transaction locks in the same order (the `MsgId` row, then the sorted `PmtInfId` rows). If PostgreSQL still detects a deadlock, the work item is retried by the work queue. The concurrency integration test covers this.

## Migration Plan

1. Flyway `V18__unique_payment_information_id.sql` creates `accepted_payment_information`. The change only adds a table, so a rollback to the previous release is safe: the older code ignores the table.
2. Deploy. From then on, ids are registered for every accepted file.
3. Update `docs/roadmap.md`: remove the `PmtInfId` row from "Remaining duplicate checks".
