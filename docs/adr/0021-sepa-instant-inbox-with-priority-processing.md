# 21. SEPA Instant inbox with priority processing

Date: 2026-10-31

## Status

Accepted

## Context

For SEPA Instant credit transfers submitted as files, the execution time of the EPC rulebook (usually 10 seconds) starts only after the bank has checked the file and turned it into single transactions (DFÜ-Abkommen Anlage 3 V3.9, 2.2.4.1). Files wait for validation on the work queue (ADR-0017), where workers claim the longest-due item first; an SCT Inst file can wait behind large SCT batches.

Whether a file holds SCT Inst is only known after parsing it in `VALIDATE`, the very step that should be prioritised. The DK separates the two before parsing by order type: CCT for SCT, CIP for SCT Inst, and a file must not mix them. On SFTP there is no order type.

## Decision

- The SFTP channel gets a second inbox for SEPA Instant files, `sepa.sftp.instant-inbound-directory`, the SFTP counterpart of order type CIP. It is optional; blank disables it.
- Intake claims files from that inbox first, marks them (`payment_file.instant_order`) and schedules their `VALIDATE` with priority `HIGH`. The claimed name `<file id>.instant-<name>` keeps the mark across a crash between claim and receipt.
- Work items carry a priority. Workers claim due `HIGH` items of a step before due `NORMAL` ones; later steps of a file inherit the priority of its earlier steps, so reporting and delivery of SCT Inst files are prioritised too.
- Validation checks the content against the inbox: a file from the SEPA Instant inbox must hold only SEPA Instant credit transfers, otherwise `AG02`. The regular inbox keeps accepting both schemes, unprioritised.
- Messages split from a container keep the inbox of their container. SEPA Instant in a container is rejected with `AG02`, since the DK does not define containers for SCT Inst (Anlage 3 V3.9, 2.4).

## Consequences

- Customers who want SCT Inst processed first upload to the SEPA Instant inbox; the directory must exist on the SFTP server before the setting is enabled.
- A flood of SCT Inst files can delay SCT files; priority only orders due items, it does not reserve workers.
- When the EBICS bank server is integrated (ADR-0009, Q1), order type CIP sets the same mark.
