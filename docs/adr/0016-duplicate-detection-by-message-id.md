# 16. Duplicate detection by message id per submitter

Date: 2026-10-31

## Status

Accepted — retention window for message ids open, see Q12

## Context

A customer must not get the same payment order executed twice because a file was uploaded twice, a client retried, or two intake instances briefly overlapped (ADR-0015). ISO 20022 gives every message a `MsgId` the initiating party must keep unique.

Questions to settle:

- **Which scope?** The `InitgPty` written in the file is self-declared by the customer and can be anything. The channel identity (SFTP account, later the EBICS customer/user id) is authenticated.
- **Which files count?** If a rejected file blocked its `MsgId`, a customer correcting an error and resubmitting would be rejected again for a file that was never processed.
- **How to stay correct under concurrency?** Two files with the same `MsgId` can be validated at the same moment, today by one instance's async listeners, later by several workers.
- **What about identical content?** An identical file repeats its `MsgId`, so a content hash adds no protection for accepted files.

## Decision

- A `MsgId` is unique per **submitter**, the authenticated channel identity (`sftp:<account>`), stored with every received file and carried in `PaymentFileReceived`.
- Only **accepted** files register their `MsgId`. A rejected file can be corrected and resubmitted under the same `MsgId`.
- Registration is an `INSERT … ON CONFLICT DO NOTHING` into `accepted_message` (primary key `submitter, message_id`), in the same transaction that records the acceptance. The database decides the race; the losing file is rejected with `DU01` (DuplicateMessageID) at `GrpHdr/MsgId`.
- Within one file, every `PmtInfId` must be unique (`DU02`, DuplicatePaymentInformationID).
- The SHA-256 of every file stays stored and indexed for audit and investigations; it is not a rejection criterion.
- Message ids are kept without expiry until the retention window is decided.

## Consequences

- Resubmissions of accepted files are rejected no matter how they arrive, including during a leader overlap and with future parallel workers.
- Duplicate detection does not trust any customer-supplied identity.
- When EBICS is integrated, its submitter identity must be mapped so that the same customer on both channels shares one scope, if the bank wants that (to be decided with the EBICS integration).
- Without an expiry, a customer whose software reuses message ids after a long time would be rejected; Q12 decides the window, after which old entries can be removed by a housekeeping job.
- `PmtInfId` uniqueness across files and End-to-End id checks are not covered yet.
