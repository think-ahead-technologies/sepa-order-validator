## Why

The DK technical validation subset GBIC_5 already makes `TwnNm` and `Ctry` mandatory in debtor and creditor postal addresses (`PostalAddress24_SCT_SCTinst_2025` and `PostalAddress24_SCT_SCTinst_2_2025`, at most two `AdrLine`), for SCT and SCT Inst at any date. The postal address rule still allows unstructured addresses for execution before 22 November 2026, so such files pass the rule and are then rejected by the DK subset with `FF01` and a parser message. The rule's date branch never leads to acceptance, and roadmap D2 (past-dated SCT Inst files escaping the switch) is not a gap in acceptance but an imprecise reason code. Customers should get `RR02`/`RR03` at the address instead.

## What Changes

- Debtor and creditor postal addresses must always be structured or hybrid (`TwnNm` and `Ctry`), reported with `RR02` (debtor) or `RR03` (creditor) at any requested execution date, for SCT and SCT Inst alike.
- The 22 November 2026 switch is removed from the rule; the rule no longer depends on any date.
- At most two `AdrLine`, codes and paths are unchanged.
- Roadmap: D2 is removed from "Decisions pending"; Q7 asks Compliance to confirm the reason codes `RR02`/`RR03` for addresses without `TwnNm` or `Ctry`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `credit-transfer-rules`: requirement "Debtor and creditor postal addresses" is replaced by "Structured debtor and creditor postal addresses", which requires structured or hybrid addresses at any date instead of switching on 22 November 2026.

## Impact

- Code: the postal address rule loses its cutover date; the rule set keeps creating it without a date.
- Tests: postal address rule unit tests, validator tests, the shared fixtures, and one end-to-end test of a past-dated SCT Inst file with an unstructured address.
- Customers: no outcome changes, since files with unstructured debtor or creditor addresses are already rejected; the reason code becomes `RR02`/`RR03` at the address instead of `FF01` with a parser message.
- Docs: `docs/roadmap.md` (D2, Q7).
