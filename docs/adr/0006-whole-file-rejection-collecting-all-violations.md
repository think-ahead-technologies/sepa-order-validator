# 6. Whole-file rejection, collecting all violations

Date: 2026-10-31

## Status

Accepted

## Context

When a file contains invalid transactions, the bank can reject the entire file or accept the valid part (`PART`). Partial acceptance is complex for customers (reconciling which payments went out) and for us (partial downstream handover, partial reports).

Customers fixing a rejected file want to see every problem at once, not one per round trip.

## Decision

- Any validation violation rejects the entire file: `pain.002` group status `RJCT`.
- Validation does not stop at the first violation. All rules are evaluated (except where an earlier failure makes later checks meaningless, e.g. XSD failure stops content rules).
- Every violation is reported with its ISO 20022 reason code and its location in the file.

## Consequences

- No partial downstream handover; simpler state model.
- One bad transaction blocks the whole file — customers must be informed of this behaviour.
- Reports for heavily broken files can be large; a cap on reported violations per file may be needed.
