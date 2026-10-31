# 20. DK XML containers are split into their messages before validation

Date: 2026-10-31

## Status

Accepted (option 1, split in the `VALIDATE` step of the container, see Decision). If the EBICS bank server already unpacks containers (open question Q1), this system never receives one and the decision has no effect.

## Context

Anlage 3 of the DFÜ-Abkommen (V3.9, chapter 9.1) defines the SEPA XML container `urn:conxml:xsd:container.nnn.001.GBIC5`. It bundles "mehrere jeweils voneinander unabhängige XML-Nachrichten" in one file:

- `<conxml>` holds a `<ContainerId>` (sender id, id type, time stamp), a creation time and 1..n `<MsgPain001>` elements, all of the same message type (`MsgPain008` and `MsgPain007` exist for other order types).
- Each `<MsgPain001>` wraps one complete `pain.001.001.09` `<Document>` and may carry a SHA-256 hash over the C14N-canonicalised document.
- Individual namespace prefixes on the embedded documents are not allowed; banks may reject such files.
- SEPA Instant credit transfers are never submitted in containers (Anlage 3, 2.4).

Today one received file is one message: one `payment_file` row, one validation outcome, one duplicate check on the `MsgId` (ADR-0016), one `pain.002` (ADR-0013) and whole-file rejection (ADR-0006). A container breaks the one-file-one-message assumption.

## Options

1. **Split into messages (recommended).** The container is checked as a whole (schema, namespace prefixes) and every embedded `Document` becomes a payment file of its own, linked to the container. From there each message runs through the unchanged pipeline: its own validation, duplicate check and `pain.002`. Only container-level faults reject the container as a whole, with one `pain.002` (`OrgnlMsgId` `NOTPROVIDED`, `FF01`).
2. **Process the container as one file, answer per message.** Validation and reporting learn about n messages per file: a message table, n outcomes and n reports per file, n duplicate checks.
3. **Whole-container rejection.** As option 2, but any violation in any message rejects all messages, extending ADR-0006 to containers.

## Decision

Option 1. It follows the DK definition of independent messages: a bad message does not hold back the others, as when the customer had sent them as separate files.

The split runs in the container's `VALIDATE` step, not in intake: splitting parses XML, all parsers come from `validation.xml.SecureXml` (ADR-0004), and `validation` already depends on `intake`, so intake cannot call into `validation` without a module cycle.

- Intake stores the container like any file. Its `VALIDATE` step recognises the container namespace, checks the envelope and splits it.
- The envelope is checked against the DK container schema `container.nnn.001.GBIC5.xsd`, unchanged. Its imports of the pain schemas are served as stubs that skip the embedded documents, so each message is schema-validated once, on its own. Containers of other message types than `MsgPain001`, messages with namespace prefixes and encodings other than UTF-8 reject the container.
- Each embedded `Document` is written out as a standalone UTF-8 document from its parsed events, with namespace declarations it inherits from the container. The container stays stored exactly as received, so the audit trail keeps the original bytes.
- Through the intake facade, each message becomes a payment file of its own, linked to the container and its position, with its own `VALIDATE` work item, in the same transaction. The container ends in status `SPLIT`, gets no status report and is excluded from the overdue-reply alert.
- Validation, duplicate detection, reporting and the work queue stay one-message-per-file.

## Consequences (of option 1)

- The optional SHA-256 hash per message is not checked yet. It is computed over the C14N 1.0 form of the document in the context of the container, and the DK example carries a placeholder value, so there is no real test vector; checking it is a follow-up.
- The customer receives n `pain.002` files for one container (plus one for a container-level fault); the file names show the container they came from.
- Duplicate detection works per embedded `MsgId`, so one container may hold messages that are accepted and others rejected as duplicates.
- Open: whether the status reports should be returned in a container (DK `CRC`) or a zip (`CRZ`) instead of single files — depends on Q1 and the delivery channel.
