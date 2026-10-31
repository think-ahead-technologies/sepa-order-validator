# 11. Own mandate store built from collections

Date: 2026-10-31

## Status

Accepted — scope of the debtor side (incoming `pacs.003`, B2B mandates) open, see Q4

## Context

SDD rules depend on mandate history: sequence type consistency, no collection after `FNAL`, expiry after 36 months without a collection, amendment indicators when mandate ID or creditor identifier change. No existing mandate system is available; this system owns the data.

## Decision

- The `mandates` module stores mandates keyed by (creditor identifier, mandate ID).
- Mandate history is updated from accepted `pain.008` collections: signature date, sequence type, last collection date, amendments.
- `validation` queries `mandates` through its public API for stateful SDD rules.
- A management API supports revocation and correction, with every change audited.

## Consequences

- Mandate rules work from day one without external dependencies.
- History only covers collections processed by this system; existing mandates need an initial data migration if one is required.
- Mandate data is personal data and falls under GDPR retention rules.
