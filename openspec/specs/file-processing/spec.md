# file-processing Specification

## Purpose

Drive every received payment file through its processing steps from persisted state, so that each file is processed exactly once and gets a reply despite crashes, hangs, transient failures and several running instances.

## Requirements

### Requirement: File status and processing steps
Every payment file SHALL have a persisted status: `RECEIVED` on receipt, then `ACCEPTED` or `REJECTED` once validated, or `SPLIT` for a container split into messages. A file SHALL be processed in the steps `VALIDATE`, `REPORT` and `DELIVER`, each persisted per file as a work item with state `PENDING`, `DONE` or `FAILED`, its attempt count, its next due time and its last error. Intake SHALL schedule `VALIDATE` in the transaction that stores the file; completing `VALIDATE` SHALL schedule `REPORT` (not for a split container), and completing `REPORT` SHALL schedule `DELIVER`. Scheduling a step that already exists for a file SHALL leave it unchanged. Processing SHALL be driven by these persisted work items only, never by in-memory notifications.

#### Scenario: A valid file runs through all steps
- **WHEN** a valid file is received
- **THEN** it ends in status `ACCEPTED`, its `VALIDATE`, `REPORT` and `DELIVER` items are `DONE`, and the customer receives an `ACCP` status report

#### Scenario: Scheduling a step twice
- **WHEN** the same step is scheduled twice for a file
- **THEN** one work item exists

### Requirement: Work items are leased to one worker at a time
A worker SHALL claim one due `PENDING` item of its step at a time under a lease of `sepa.processing.<step>.lease`, counting the attempt at the claim. Items leased by others SHALL be skipped, not waited for. An item whose lease expired SHALL be claimable again. Completing, retrying or failing an item SHALL succeed only for the worker that holds its current lease; a worker that lost its lease SHALL change nothing. Lease times SHALL use the database clock. A step's result, the file status change, the audit entry and the scheduling of the next step SHALL be recorded in one transaction together with completing the item, so a crash before completion leaves no trace.

#### Scenario: Concurrent workers claim distinct items
- **WHEN** several workers claim items at the same time
- **THEN** each item is claimed by at most one worker

#### Scenario: An expired lease is taken over
- **WHEN** a worker's lease expires and another worker claims the item
- **THEN** the first worker can no longer complete, retry or fail it

#### Scenario: A delivery leased by a killed worker
- **WHEN** an instance is killed while delivering a report and its lease is left behind
- **THEN** another instance claims the item once the lease expires and the customer receives exactly one report

### Requirement: Workers run on every instance with bounded concurrency
Every instance SHALL run, per step, at most `sepa.processing.<step>.workers` items at once and SHALL look for due items every `sepa.processing.<step>.poll-interval` (default 1 second) while none are found. Startup SHALL fail when settings for any step are missing, when workers or max-attempts are below 1, when a duration is not positive, when the timeout is not below the lease, or when the maximum backoff is below the initial backoff. Startup SHALL also fail when the database connection pool (`spring.datasource.hikari.maximum-pool-size`) is smaller than the sum of all step workers plus 4, naming the size needed. Defaults:

| Step | workers | lease | timeout | max-attempts | initial-backoff | max-backoff |
|---|---|---|---|---|---|---|
| `VALIDATE` | 2 | 10 min | 5 min | 3 | 10 s | 1 min |
| `REPORT` | 2 | 2 min | 1 min | 10 | 10 s | 10 min |
| `DELIVER` | 2 | 2 min | 1 min | 10 | 10 s | 10 min |

#### Scenario: Connection pool too small
- **WHEN** the connection pool is smaller than all step workers plus 4
- **THEN** the application refuses to start and names the pool size needed

#### Scenario: Timeout not below the lease
- **WHEN** a step's timeout is equal to or longer than its lease
- **THEN** the application refuses to start

### Requirement: Priority for SEPA Instant files
Work items SHALL carry a priority. A worker SHALL claim due high-priority items of its step before due normal ones, and among equal priority the longest-due first. A file's first step SHALL get high priority when it comes from the SEPA Instant inbox, and its later steps SHALL keep the priority of its earlier steps.

#### Scenario: A priority item is claimed first
- **WHEN** a high-priority item becomes due after older normal items of the same step
- **THEN** the high-priority item is claimed first

#### Scenario: Later steps keep the priority
- **WHEN** a high-priority file's `REPORT` and `DELIVER` steps are scheduled
- **THEN** they carry high priority too

### Requirement: Retries with exponential backoff
A failed attempt SHALL be retried after a backoff that starts at `sepa.processing.<step>.initial-backoff` and doubles with every failed attempt up to `sepa.processing.<step>.max-backoff`; the item SHALL NOT be due before its backoff has passed. The last error SHALL be kept on the item for operations. After `sepa.processing.<step>.max-attempts` the item SHALL become `FAILED`, an ERROR SHALL be logged, and it SHALL NOT be claimed again unless re-driven, unless the step records a fallback outcome instead. Because attempts are counted at the claim, an item whose attempts all ended without a recorded outcome (for example by crashing the worker) SHALL NOT be attempted again once its attempts are used up.

#### Scenario: Backoff doubles and is capped
- **WHEN** attempts of a step fail one after another
- **THEN** the delay before each retry doubles from the initial backoff and never exceeds the maximum backoff

#### Scenario: A retried item waits for its backoff
- **WHEN** an attempt fails and is scheduled for retry
- **THEN** the item is not claimed again before its backoff has passed

#### Scenario: Delivery recovers once the outbound channel recovers
- **WHEN** the customer's outbound directory is not writable for a while
- **THEN** delivery is retried, and once the directory is writable again the report is delivered exactly once

#### Scenario: A failed item is never claimed again
- **WHEN** an item has become `FAILED`
- **THEN** no worker claims it

### Requirement: Every file gets a reply
`VALIDATE` SHALL never end without an outcome for the customer. When all its attempts fail, or a file has used up its attempts without a recorded outcome, the system SHALL record the technical rejection defined by the file-validation capability as the step's outcome, without loading the file again, and continue with `REPORT` and `DELIVER`. A validation that fails unexpectedly SHALL NOT be retried, because a retry would fail the same way.

#### Scenario: All validation attempts fail
- **WHEN** every `VALIDATE` attempt of a file fails
- **THEN** after the last attempt the file is `REJECTED`, its `VALIDATE` item is `DONE` rather than `FAILED`, exactly one `PaymentFileValidated` audit entry exists, "VALIDATE gave up after" is logged, and its report is created and delivered

### Requirement: Step timeouts
Each attempt SHALL receive a deadline of `sepa.processing.<step>.timeout` from its start. An attempt still running halfway between its timeout and the end of its lease SHALL be interrupted and count as a failed attempt, so no second worker can start on the item while it runs. For `VALIDATE`, the outcome of a validation past its deadline is defined by the file-validation capability.

#### Scenario: A delivery hanging on an unresponsive SFTP server
- **WHEN** the SFTP server stops responding during a delivery
- **THEN** the attempt is interrupted with an error stating it did not finish within the timeout, and once the server responds again the report is delivered once and the item is `DONE`

### Requirement: Repeated steps are safe
Repeating a step for a file whose step result is already recorded SHALL NOT change the result: a repeated `VALIDATE` SHALL NOT validate the file again, a repeated `REPORT` SHALL NOT create a second report or message id, and a repeated `DELIVER` SHALL re-send the identical stored report under the same name.

#### Scenario: A re-driven VALIDATE item
- **WHEN** a `VALIDATE` item of an already validated file runs again
- **THEN** the file's outcome and status are unchanged

#### Scenario: A re-driven REPORT item
- **WHEN** a `REPORT` item of a file that already has a report runs again
- **THEN** no second report is created

### Requirement: Manual re-drive
Operations SHALL be able to make a `FAILED` or `DONE` work item due again with fresh attempts through the database function `redrive_work_item(file id, step, operator, reason)`. It SHALL require a non-blank operator and reason, SHALL refuse items that are still `PENDING` or do not exist, and SHALL write a `WorkItemRedriven` audit entry with operator, reason and previous state in the same statement.

#### Scenario: Re-driving a failed item
- **WHEN** an operator re-drives a `FAILED` item with name and reason
- **THEN** the item is `PENDING` with zero attempts and due now, and a `WorkItemRedriven` audit entry names the operator, reason and previous state

#### Scenario: A failed delivery is delivered once after re-drive
- **WHEN** a `DELIVER` item failed for good and is re-driven after the channel recovered
- **THEN** the customer receives the report exactly once

#### Scenario: Refusing to re-drive a pending item
- **WHEN** an operator re-drives an item that is still `PENDING` or does not exist
- **THEN** the function fails and nothing changes

### Requirement: Audit entries with each transition
The system SHALL record a file's transitions in an append-only audit trail, each entry written in the same transaction that makes the transition, so an entry exists exactly when the transition happened: `PaymentFileReceived` on receipt, `PaymentFileValidated` or `PaymentFileSplit` on validation, `StatusReportDelivered` on delivery, and `WorkItemRedriven` on a manual re-drive. Failed attempts SHALL leave no audit entry. Audit entries SHALL NOT be updatable, deletable or truncatable.

#### Scenario: Complete audit trail of a reported file
- **WHEN** a file has been received, validated and its report delivered
- **THEN** its audit trail holds exactly `PaymentFileReceived`, `PaymentFileValidated` and `StatusReportDelivered`

#### Scenario: Rolled-back transition
- **WHEN** the transaction of a transition rolls back
- **THEN** no audit entry for it remains

#### Scenario: Tampering is refused
- **WHEN** anyone tries to update, delete or truncate audit entries
- **THEN** the database refuses with an append-only error

### Requirement: Housekeeping and shutdown
`DONE` work items SHALL be deleted once completed longer ago than `sepa.processing.retention` (default 7 days), checked every `sepa.processing.housekeeping-interval` (default 1 hour) on every instance; `FAILED` items SHALL be kept until re-driven. On shutdown an instance SHALL stop claiming, wait briefly for running items, and leave items it could not finish to be taken over after their lease expires, without logging errors.

#### Scenario: Only old DONE items are deleted
- **WHEN** housekeeping runs
- **THEN** `DONE` items completed before the retention are deleted, while newer `DONE`, `PENDING` and `FAILED` items remain

#### Scenario: Clean shutdown
- **WHEN** the application is stopped
- **THEN** no ERROR is logged
