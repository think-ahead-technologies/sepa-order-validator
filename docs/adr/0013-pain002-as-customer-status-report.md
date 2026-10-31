# 13. pain.002 as the customer status report

Date: 2026-10-31

## Status

Accepted

## Context

Customers submitting files via EBICS and SFTP expect a machine-readable status report they can process in their ERP / treasury systems. ISO 20022 defines `pain.002` (Customer Payment Status Report) for this; the DK specification defines its German usage.

## Decision

- Every file reaching a terminal state (`ACCEPTED`, `REJECTED`) produces a `pain.002`.
- Group status `ACCP` or `RJCT` (see ADR-0006); for rejections, every violation is reported with its ISO 20022 status reason code at the group, payment-information or transaction level where it occurred.
- The `pain.002` version matches the version family of the submitted file as specified by DK.
- Reports are delivered through the channel the file arrived on and stored for audit.

## Consequences

- Customers process results automatically, with no proprietary format.
- Internal violation codes must map to ISO 20022 external reason codes; unmapped cases need a documented fallback (e.g. `NARR` with additional information).
