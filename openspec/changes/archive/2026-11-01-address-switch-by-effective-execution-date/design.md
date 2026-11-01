## Context

See proposal.md for why. Validation runs the ISO schema, then the rules, then the DK subset GBIC_5 only on files the rules pass (baseline design A9). The bundled GBIC_5 schema types debtor addresses as `PostalAddress24_SCT_SCTinst_2025` and creditor addresses as `PostalAddress24_SCT_SCTinst_2_2025`; both declare `TwnNm` and `Ctry` without `minOccurs="0"` and allow at most two `AdrLine`. The postal address rule allows unstructured addresses for a requested execution date before 22 November 2026, so those files reach the DK subset and get `FF01` with a parser message. This was found while implementing an effective-execution-date variant of the rule, whose "accepted before the switch" case failed at the DK subset.

## Goals / Non-Goals

**Goals:**
- Precise `RR02`/`RR03` for every debtor or creditor address the DK subset would reject for its form or its number of lines.
- A rule that matches the DK subset and depends on no date.

**Non-Goals:**
- Other GBIC_5 restrictions that only the DK subset covers today.
- Addresses of ultimate parties and agents.

## Decisions

**One address requirement for all dates, aligned with GBIC_5.** The rule requires `TwnNm` and `Ctry` and at most two `AdrLine`, whatever the execution date and scheme. Following baseline design A9, a DK subset restriction the customer is likely to hit gets a precise reason code from a rule instead of `FF01`.
*Alternatives:* decide the 22 November switch by an effective execution date (the later of requested date and today), which keeps a branch whose acceptance the DK subset always overrides; leave the rule as it is, which keeps `FF01` with a parser message for every unstructured address before the switch.

**The rule keeps no date and no "today".** With no date dependency, unit, integration and end-to-end tests run with the real clock and stay deterministic; no fixed application clock is needed.

## Risks / Trade-offs

- [A later GBIC generation could relax the address requirement] → the TVS file is replaced per GBIC generation and rules made redundant or stricter than the TVS are reviewed then (baseline design A9).
- [The EPC implementation guidelines still allow unstructured addresses until November 2026] → the bank applies the DK specification, which is stricter; the requirement cites both sources.

## Migration Plan

Deploy at any time; no data migration. Rejections stay rejections, only their reason codes change. Rollback: revert the commit.
