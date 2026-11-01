## 1. Spec review

- [x] 1.1 Remove duplicated requirements so each behaviour has one owner: the size limit stays in `file-intake` (drop it from `xml-security` or reduce it there to "never parsed"); the validation deadline and the technical `FF01` rejection stay in `file-validation` (shorten the overlapping text in `file-processing` to a reference). Verify with `grep -n 'max-file-size\|deadline' openspec/changes/adopt-openspec-baseline/specs/*/spec.md` showing one owning requirement each.
- [x] 1.2 Review every requirement against the code and the test it names, capability by capability. Verify by ticking off each spec file here: file-intake, container-split, format-acceptance, xml-security, file-validation, credit-transfer-rules, status-report, file-processing, observability, supply-chain.
- [x] 1.3 Run `openspec validate adopt-openspec-baseline --strict` and verify it reports the change as valid.

## 2. Discrepancies found while drafting

Specs follow the code (design D1). Items marked *decide* may be bugs: each is either accepted as specified or recorded in `docs/roadmap.md` as a follow-up change. This change does not alter behaviour.

- [x] 2.1 *Decide:* SCT Inst amounts are capped at 999,999,999.99 (`AM02`), although the SCT Inst Rulebook 2025 has no scheme maximum (Q15). Verify the decision is recorded in the roadmap or the spec text is confirmed.
- [x] 2.2 *Decide:* the 22 November 2026 structured-address switch uses `ReqdExctnDt`; an SCT Inst file dated before the switch but submitted after it still passes with unstructured addresses. Verify the decision is recorded.
- [x] 2.3 *Decide:* IBAN rule messages put the IBAN value into the pain.002 `AddtlInf` sent to the customer. Verify the decision is recorded (acceptable to the customer channel, or masked in a follow-up).
- [x] 2.4 *Decide:* `REPORT` writes no audit entry; the trail holds `PaymentFileReceived`, `PaymentFileValidated`, `StatusReportDelivered`. Verify the decision is recorded.
- [x] 2.5 *Decide:* an item reaching `FAILED` only logs an ERROR (no metric), and the "one open work item per non-terminal file" invariant is not monitored. Verify the decision is recorded.
- [x] 2.6 *Decide:* a repeated delivery renames over an existing report instead of treating identical content as delivered. Verify the decision is recorded.
- [x] 2.7 Record as accepted, no action: one fixed rule set without rulebook effective dates; only file statuses `RECEIVED`, `ACCEPTED`, `REJECTED`, `SPLIT`; pain.002 always `pain.002.001.10` with all reasons at group level; `NARR` used only for the "more violations" summary; up to 1,001 `FF01` entries from schema validation; a DOCTYPE file rejected by format detection with a generic `FF01`; duplicate `MsgId` (`DU01`) checked only on otherwise acceptable files; unstructured addresses with optional `Ctry`. Verify each appears in design.md or the specs as stated.

## 3. Project context and roadmap

- [x] 3.1 Fill `openspec/config.yaml` `context` from design doc sections 1–5 and 9–12 and former ADR-0002, 0003, 0008, 0012 (purpose, scope, stack, modules, persistence, security, test strategy without mocks, logging rules). Verify `openspec instructions proposal --change adopt-openspec-baseline --json` shows the context.
- [x] 3.2 Create `docs/roadmap.md` with ABOUTME-free markdown: remaining delivery plan (SDD and mandates, EBICS, screening and review, VoP), unimplemented catalogue rows with their intended reason codes and sources, the supply-chain target not built (SBOM, image signing, admission), the decisions deferred in section 2, and Q1–Q15 with the "Affects" column pointing at capabilities. Verify no `ADR-` string remains in it.

## 4. Repoint references

- [x] 4.1 Repoint ADR citations in comments under `src/main/java` (about 20 files: intake, processing, reporting, validation, `LogContext`, `LogMasking`, the application class) to capability specs or design A-sections, per design D6. Verify `grep -rnE 'ADR-?0[0-9]{3}' src/main/java` returns nothing.
- [x] 4.2 Repoint citations in `src/main/resources/application.yaml`, `ModularityTests` and `PaymentFileValidatorTest`. Leave `db/migration/V11`–`V14` unchanged (design D6). Verify `grep -rnE 'ADR-?0[0-9]{3}' src | grep -v db/migration` returns nothing.
- [x] 4.3 Repoint ADR and design links in `docs/operations/redrive-work-items.md`. Verify no link into `docs/adr` or `docs/design` remains.
- [x] 4.4 Update `CLAUDE.md`: replace the design and ADR pointers with OpenSpec (`openspec/specs/`, changes via `/opsx:propose`) and the archived baseline design for rationale; update "Processing steps get the file id from their worker (ADR-0018)" and the ADR-0004/0017 mentions. Verify `grep -n 'ADR\|docs/design' CLAUDE.md` returns nothing.

## 5. Remove the old documents and verify

- [x] 5.1 Delete `docs/adr/` and `docs/design/`. Verify `grep -rn 'docs/adr\|docs/design' . --exclude-dir=.git --exclude-dir=build --exclude-dir=archive` returns nothing outside this change.
- [x] 5.2 Run `mise exec -- ./gradlew build` and verify all tests pass with pristine output.
- [x] 5.3 Commit as `docs: adopt OpenSpec as the single source of truth` and archive the change with `/opsx:archive`; verify `openspec list --specs` lists the ten capabilities.
