## Why

A `PmtInfId` is checked for uniqueness only within one file today. A customer who resubmits a payment information block in a second file under a new `MsgId`, for example after splitting or re-batching a file, gets it accepted twice, and downstream processing may execute the same batch twice. This change delivers the roadmap entry "Remaining duplicate checks: `PmtInfId` unique per submitter across files" (`DU02`). It does not resolve Q12: the uniqueness window follows whatever Q12 decides for `MsgId`, which until then means no expiry.

## What Changes

- Every accepted file registers its `PmtInfId` values for its submitter (the authenticated channel identity), in the same transaction that registers its `MsgId`.
- A file that passes every other check but contains a `PmtInfId` that an accepted file from the same submitter already used is rejected with `DU02` at each affected `PmtInf[n]/PmtInfId`.
- Rejected files register neither their `MsgId` nor their `PmtInfId` values, so a corrected file can reuse them. A file rejected for a duplicate `PmtInfId` also does not register its `MsgId`.
- A file whose `MsgId` is already a duplicate (`DU01`) gets only `DU01`. Its `PmtInfId` values are not checked against other files, so resubmitting an identical file still produces a report with only `DU01`.
- The same `PmtInfId` from different submitters does not conflict. When several files from one submitter that share a `PmtInfId` are accepted concurrently, exactly one is accepted.
- Messages split from a DK container are checked like separately submitted files, so two messages of one container that share a `PmtInfId` conflict.
- The within-file `DU02` rule stays unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `file-validation`: the stage order names payment information id detection next to message id detection, and a new requirement makes `PmtInfId` unique per submitter among accepted files (`DU02`).

## Impact

- `validation` module: `ValidationResult` carries the file's payment information ids and their locations. `PaymentFileValidation.record` registers them next to the `MsgId`. There is a new repository next to `AcceptedMessages`.
- Database: a new Flyway migration adds the table `accepted_payment_information` (submitter, payment information id, file id, accepted at) with a primary key on submitter and id.
- Status reports: `DU02` can now appear for a file whose ids are all distinct within the file. The pain.002 format does not change.
- Existing data: files accepted before the migration have no registered `PmtInfId` values, so a reuse of one of their ids goes undetected (see design).
- `docs/roadmap.md`: the `PmtInfId` row leaves "Remaining duplicate checks"; only `DU04` remains.
