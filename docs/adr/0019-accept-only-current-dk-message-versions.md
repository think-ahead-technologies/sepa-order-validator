# 19. Accept only the current DK message versions

Date: 2026-10-31

## Status

Accepted. It supersedes the list of accepted formats in [ADR-0005](0005-format-detection-and-versioned-rule-sets.md); detection by namespace and versioned rule sets stay as decided there.

## Context

ADR-0005 listed every format the DK had ever specified: `pain.001.001.03`, `pain.001.001.09`, `pain.001.003.03`, `pain.008.001.02`, `pain.008.001.08`, `pain.008.003.02` and DK container files. The DK format lifecycle ("SEPA-Formatversionen im Verfahren DFÜ mit Kunden", version 2.0 of 24 September 2026) and Anlage 3 of the DFÜ-Abkommen (version 3.9, valid for SEPA from 5 October 2025) show:

- `pain.001.003.03` and `pain.008.003.02` (Anlage 3 V2.7 to V2.9) reached the end of their life cycle in November 2022.
- Since Anlage 3 V3.7 (for SEPA from 17 March 2024) the current versions are `pain.001.001.09` and `pain.008.001.08`; V3.9 adds the TVS generation GBIC_5 without a new message version.
- `pain.001.001.03` and `pain.008.001.02` (GBIC_1 to GBIC_3) are "vorübergehend weiter verwendbar"; a new end date will be set by the EPC and SWIFT. Anlage 3 (2.4) allows the predecessor version only in a transition period and by bilateral agreement with the bank.

Supporting `pain.001.001.03` would need its own schema, parser differences (`BIC` instead of `BICFI`, a plain execution date, the 2009 postal address type) and, per ADR-0013, `pain.002.001.03` status reports next to `pain.002.001.10`, for a version that is being retired.

## Decision

- Credit transfers are accepted only as `pain.001.001.09`.
- `pain.001.001.03`, `pain.001.003.03` and every other version are rejected as an unsupported format with `FF01`, as today.
- Status reports are written only as `pain.002.001.10`.
- For direct debits (delivery plan step 5) the same reasoning suggests `pain.008.001.08` only; this is confirmed when SDD is built.

## Consequences

- One message version per scheme: one schema, one parser, one status report version.
- Customers still sending `pain.001.001.03` must migrate before using this system. If the business finds such customers, this decision is revisited (open question Q6).
- When the DK publishes a new message version, it is added as a new format and rule set as described in ADR-0005.
