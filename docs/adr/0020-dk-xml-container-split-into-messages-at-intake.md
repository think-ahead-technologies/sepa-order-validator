# 20. DK XML containers are split into their messages at intake

Date: 2026-10-31

## Status

Accepted (option 1). If the EBICS bank server already unpacks containers (open question Q1), this system never receives one and the decision has no effect.

## Context

Anlage 3 of the DFÜ-Abkommen (V3.9, chapter 9.1) defines the SEPA XML container `urn:conxml:xsd:container.nnn.001.GBIC5`. It bundles "mehrere jeweils voneinander unabhängige XML-Nachrichten" in one file:

- `<conxml>` holds a `<ContainerId>` (sender id, id type, time stamp), a creation time and 1..n `<MsgPain001>` elements, all of the same message type (`MsgPain008` and `MsgPain007` exist for other order types).
- Each `<MsgPain001>` wraps one complete `pain.001.001.09` `<Document>` and may carry a SHA-256 hash over the C14N-canonicalised document.
- Individual namespace prefixes on the embedded documents are not allowed; banks may reject such files.
- SEPA Instant credit transfers are never submitted in containers (Anlage 3, 2.4).

Today one received file is one message: one `payment_file` row, one validation outcome, one duplicate check on the `MsgId` (ADR-0016), one `pain.002` (ADR-0013) and whole-file rejection (ADR-0006). A container breaks the one-file-one-message assumption.

## Options

1. **Split at intake (recommended).** Intake recognises the container namespace, validates the container against its schema, checks each message's hash when present and stores every embedded `Document` as its own payment file, linked to the container. From there each message runs through the unchanged pipeline: its own validation, duplicate check and `pain.002`. Only container-level faults (schema, hash, prefixes) reject the container as a whole, with one `pain.002` (`OrgnlMsgId` `NOTPROVIDED`, `FF01`).
2. **Process the container as one file, answer per message.** Validation and reporting learn about n messages per file: a message table, n outcomes and n reports per file, n duplicate checks.
3. **Whole-container rejection.** As option 2, but any violation in any message rejects all messages, extending ADR-0006 to containers.

## Decision

Option 1, split at intake:

- It follows the DK definition of independent messages; a bad message does not hold back the others, as when the customer had sent them as separate files.
- Validation, duplicate detection, reporting and the work queue stay one-message-per-file; only intake learns about containers.
- The embedded documents are copied byte for byte from the container, so the audit trail keeps the original content; the container itself is stored as received.

## Consequences (of option 1)

- New in intake: container schema, hash check (C14N 1.0 via `java.xml.crypto`, created through `SecureXml`), splitting streamed per message to respect the file size limit, and a link from each message file to its container.
- The customer receives n `pain.002` files for one container (plus one for a container-level fault); the file names show the container they came from.
- Duplicate detection works per embedded `MsgId`, so one container may hold messages that are accepted and others rejected as duplicates.
- Open: whether the status reports should be returned in a container (DK `CRC`) or a zip (`CRZ`) instead of single files — depends on Q1 and the delivery channel.
