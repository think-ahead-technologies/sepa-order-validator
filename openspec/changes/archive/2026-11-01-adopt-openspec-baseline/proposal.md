## Why

System behaviour is described in three overlapping places: the rule catalogue and sections of `docs/design/payment-order-validator.md`, 22 ADRs that mix decisions with requirements, and the tests. They already drift (superseded ADRs, "not yet implemented" rows next to built rules, a duplicated ADR index row), and every feature needs edits in several documents. Adopting OpenSpec now, while the system is still small, gives one source of truth for behaviour before SDD, EBICS and screening triple its size.

## What Changes

- Capture the behaviour that is built and tested today as OpenSpec capability specs. Only implemented behaviour is specified; planned rules and features are not.
- Record the architecture rationale that has no place in a spec (modular monolith, hardened XML, whole-file rejection, PostgreSQL leader election, work queue instead of module events, container split) in this change's `design.md`, which becomes the archived decision record.
- Move project background, stack and conventions into `openspec/config.yaml` `context`.
- Move the delivery plan, unimplemented catalogue rows, proposed ADRs (EBICS, screening) and open questions Q1–Q15 to `docs/roadmap.md`.
- Repoint ADR and design document references in source code comments and `CLAUDE.md` to capability specs.
- **BREAKING** (documentation only): delete `docs/adr/` and `docs/design/`. ADR numbers stop being valid references.
- No runtime behaviour changes.

## Capabilities

### New Capabilities

- `file-intake`: SFTP polling with verified host keys, finished-upload detection, file size limit, leader election across instances, the SEPA Instant inbox, storing received files, and the overdue-reply alert.
- `container-split`: DK XML containers split into their messages before validation, container hash checks, each message answered on its own.
- `format-acceptance`: detection by namespace, acceptance of only the current DK message versions, XSD validation of the namespace's schema.
- `xml-security`: hardened parsing of untrusted XML (no external entities or DTDs, size and processing time limits).
- `file-validation`: whole-file rejection collecting all violations, group and payment information checks, duplicate detection by message id, the DK technical validation subset as final gate.
- `credit-transfer-rules`: transaction rules for SCT and SCT Inst (IBAN, BIC, amount, currency, character set, remittance, names, addresses, execution date, local instrument, service level).
- `status-report`: the pain.002 customer status report, its content and delivery to the submitter.
- `file-processing`: processing of each file through persisted steps with leases, retries, priority for SEPA Instant, timeouts and audit entries written with each step.
- `observability`: ECS JSON logs without payment data, file correlation, operational endpoints.
- `supply-chain`: verification of every dependency and of the build tool distribution against recorded signatures and checksums. SBOM, image signing and admission control are not built and go to the roadmap.

### Modified Capabilities

None. There are no existing specs.

## Impact

- Documentation: `docs/adr/` and `docs/design/` removed; `docs/roadmap.md` added; `openspec/config.yaml` filled; `CLAUDE.md` updated to point at OpenSpec.
- Source: comments in about 28 files under `src/` that cite ADRs are repointed. No code behaviour changes; the build and all tests stay green.
- Process: future work starts as an OpenSpec change (`/opsx:propose`) instead of a `docs:` commit adding an ADR.
