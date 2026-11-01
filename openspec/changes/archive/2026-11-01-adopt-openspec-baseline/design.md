## Context

See proposal.md for why. Today the documentation is the design document (`docs/design/payment-order-validator.md`, 286 lines), 22 ADRs (`docs/adr/`) and one runbook (`docs/operations/redrive-work-items.md`). About 28 source files and `CLAUDE.md` cite ADR numbers in comments. The specs in this change describe the behaviour; this document has two jobs:

1. Describe how the move to OpenSpec is carried out (Decisions D1–D6).
2. Keep the architecture rationale that a spec cannot hold (Decisions A1–A9). Once this change is archived, this file is the decision record that replaces the ADRs. It condenses them; the original texts stay available in git history (last commit before deletion).

## Goals / Non-Goals

**Goals:**
- One place for behaviour (`openspec/specs/`), one place for the rationale behind the existing architecture (this file, archived), one place for what is not built yet (`docs/roadmap.md`).
- No dangling references: every ADR or design document citation in code, `CLAUDE.md` and the runbook points at a capability spec or is removed when it adds nothing (applied migrations excepted, see D6).
- Build and all tests stay green; no behaviour changes.

**Non-Goals:**
- Specifying behaviour that is not implemented (SDD, mandates, EBICS, screening, VoP, cross-file `PmtInfId` and End-to-End id checks).
- Resolving open questions Q1–Q15; they move unchanged in substance.
- Changing code beyond comments.

## Decisions

### Migration

**D1. Specs describe only built behaviour.** Every requirement is backed by code and, wherever possible, by an existing test. Catalogue rows marked "not yet implemented", proposed ADRs (0009 EBICS, 0010 screening) and design doc sections 7–8 (screening, mandates) go to `docs/roadmap.md`. Where an ADR and the code disagree, the spec follows the code and the discrepancy is listed in tasks.md for a decision.
*Alternative:* specify the target state including planned rules. Rejected: specs would claim behaviour the system lacks, and there would be no signal for what a future change adds.

**D2. Capabilities follow observable behaviour, not Java modules.** Ten capabilities (proposal). Validation is split into `format-acceptance`, `xml-security`, `file-validation` and `credit-transfer-rules` because these change for different reasons (DK format lifecycle, security, group checks, EPC rulebook). SCT and SCT Inst share one capability since most rules apply to both; differences are separate requirements. Audit is part of `file-processing` because audit entries are written in step transactions.
*Alternative:* one capability per Spring Modulith module. Rejected: `validation` would hold more than half of all requirements.

**D3. Rationale lives in this design.md, archived with the change.** OpenSpec has no artifact type for long-lived rationale. Writing the baseline as a change makes its design.md the record, found under `openspec/changes/archive/<date>-adopt-openspec-baseline/design.md`. Each spec's `Purpose` stays short and behavioural. Future architecture decisions go into the design.md of the change that makes them.
*Alternative:* keep slim ADRs for rationale only. Considered and rejected by the team in favour of a single system.

**D4. Project background moves to `openspec/config.yaml` `context`.** Purpose, scope, stack, module rules, the test strategy without mocks (former ADR-0012), logging rules and conventions. `CLAUDE.md` keeps tooling and points to OpenSpec for behaviour and to this record for rationale.

**D5. `docs/roadmap.md` is the only remaining planning document.** It holds the remaining delivery plan steps (SDD, EBICS, screening, VoP), unimplemented catalogue rows with their intended reason codes and sources, and open questions Q1–Q15 with their "Affects" column repointed from ADR numbers to capabilities. A roadmap entry turns into an OpenSpec change when work starts. There is no git remote, so issues are not an option today.

**D6. References are repointed, not deleted.** A comment citing `ADR-00xx` is rewritten to cite the capability (`see spec file-processing`) when the reference explains behaviour, or to cite this record (`see adopt-openspec-baseline design A6`) when it explains a design choice. A comment whose reference adds nothing beyond the code keeps its explanatory text and loses only the citation. The runbook stays; it is an operating procedure, not a specification.
*Exception:* applied Flyway migrations (`V11`–`V14` cite ADRs) are not edited. Flyway checksums the whole file, comments included, so a changed migration fails validation on every existing database. Their ADR citations stay as historical references, resolvable through git history and the A-numbered sections below (ADR-0017 → A6).

### Architecture rationale carried over

**A1. Modular monolith with Spring Modulith** (former ADR-0002). Intake, validation, reporting, audit and processing share one file lifecycle and one transactional database, built by a small team. Microservices would add distributed transactions and operational overhead before the real seams are known. One deployable with module boundaries verified in the build (`ModularityTests`); a module (most likely future screening) can be extracted later. All modules scale together, acceptable at file-level volumes. Stack: Java 25, Spring Boot 4, Gradle (former ADR-0003), PostgreSQL with Flyway as the only schema path and database constraints for uniqueness (former ADR-0008).

**A2. Streaming, hardened XML** (former ADR-0004). Files come from outside the bank and can hold 100,000+ transactions. All parsers and schema validators come from one factory (`SecureXml`) that disables DTDs and external entities, enables secure processing and serves only bundled schemas: XXE, entity expansion and schema retrieval are closed in one place. Parsing streams one transaction at a time and accumulates group totals. *Known limit:* the raw file is still held whole in memory and stored as `BYTEA`, so memory per file is bounded by the maximum file size, not flat; truly flat memory needs stream storage (object storage or large objects), deferred until volumes require it. Violations are capped at 1,000 per file.

**A3. Whole-file rejection, all violations collected** (former ADR-0006). Partial acceptance (`PART`) is hard to reconcile for customers and complicates downstream handover and reports. Any violation rejects the file, and all rules run so the customer sees every problem at once; a schema failure stops content rules because they would be meaningless. *Trade-off:* one bad transaction blocks the file, customers must know this.

**A4. Format by namespace, one version per scheme** (former ADR-0005, ADR-0019). The format is detected from the root namespace before anything else; unknown namespaces get `FF01`. Only `pain.001.001.09` is accepted and only `pain.002.001.10` written. `pain.001.003.03` ended in 11/2022; `pain.001.001.03` is only temporarily usable by bilateral agreement (DK format lifecycle 2.0, 24 September 2026). Supporting it would mean a second schema, parser differences and a second pain.002 version for a version being retired. Revisit if customers still send it (Q6). The original idea of rule sets selected by rulebook effective date is not built; date-dependent rules (e.g. the 22 November 2026 address switch) decide by requested execution date inside the rule.

**A5. Leader election with PostgreSQL locks** (former ADR-0015). Several instances poll the same SFTP inbox; a two-instance test reproduced double processing. Chosen: Spring Integration's lock-registry leader initiator on the JDBC lock registry (`INT_LOCK`, role `sftp-intake`). Rejected: the OpenShift Lease API (RBAC, cluster coupling, not testable without a cluster) and ShedLock (no stable leader state). Leader election is not a correctness guarantee: a leader stalled past the lock time-to-live can overlap a new one. The safety nets are the atomic claim-by-rename with idempotent storage per file id and `MsgId` duplicate detection. Instance clocks must be NTP-synchronised.

**A6. Processing driven by persisted state on a PostgreSQL work queue** (former ADR-0017, superseding the event chain of ADR-0007). An architecture review on 2026-10-31 found the Spring Modulith event chain unsuitable as a work queue on several instances: resubmission restarted runs still active elsewhere, republishing on restart duplicated steps on every rolling deploy, recovery depended on registry staleness settings, `@Async` listeners had no concurrency bound and timed out on the connection pool under backlog, and audit entries written by a separate listener could duplicate or go missing. The registry is a transactional outbox, not a queue.

Decided: one `work_item` row per file and step; bounded workers per step claim due items with `FOR UPDATE SKIP LOCKED`, under a lease whose token fences completion. Each step works outside a transaction and completes in one short transaction that stores its result, moves the file status, writes exactly one audit entry and schedules the next step. Database time drives leases, so instance clock skew does not matter. Side effects (delivery) are made safe to repeat via a staging directory and rename. `processing` depends on no other module; step modules depend on it.

Rejected alternatives: hardening the event chain (processing would still depend on events arriving), a message broker (new infrastructure, a second source of truth next to the database), a workflow engine (heavyweight for three steps), and db-scheduler/JobRunr (a library-owned task table cannot share the completion transaction with file status and audit). Revisit the library option if the custom worker runtime grows beyond a claim query and a worker loop. *Trade-offs:* up to one poll interval of latency per step; the worker runtime is ours to own and test.

**A7. Duplicate detection by `MsgId` per authenticated submitter** (former ADR-0016). The `InitgPty` in the file is self-declared; the channel identity (`sftp:<account>`) is authenticated, so it is the scope. Only accepted files register their `MsgId`, so a corrected rejected file can be resubmitted. The database decides concurrent races (`ON CONFLICT DO NOTHING` in the acceptance transaction). The file hash is stored for audit but is not a rejection criterion, since an identical file repeats its `MsgId` anyway. Ids are kept without expiry until Q12 sets a window. When EBICS arrives, its identity must map to the same scope if the bank wants one scope per customer.

**A8. DK containers split into messages in the `VALIDATE` step** (former ADR-0020). Anlage 3 V3.9, 9.1 defines a container as independent messages. Options were: split into messages (chosen), process the container as one file with n reports, or reject the whole container on any violation. Splitting keeps validation, duplicate detection, reporting and the work queue one-message-per-file, and a bad message does not hold back the others. The split runs in validation, not intake, because it parses XML (only `SecureXml` may) and `intake` cannot depend on `validation` without a cycle. Messages are written in Canonical XML 1.0, the form the optional SHA-256 hash is computed over, checked against libxml2. If the EBICS bank server unpacks containers (Q1), this path is never used. Whether reports should return as a container (`CRC`) or zip (`CRZ`) is open.

**A10. Supply chain target, partly built** (former ADR-0014). Built: Gradle dependency verification (PGP or SHA-256, keyring in the repository) and the wrapper distribution checksum, so a tampered or unrecorded artifact fails the build. Intended but not built, and moved to the roadmap: CycloneDX SBOM in the jar and image, cosign signing of the image by digest with a Vault Transit key held only by CI, SLSA provenance, admission control on OpenShift (Q10, Q11).

**A9. SEPA Instant inbox with priority; DK subset as final gate** (former ADR-0021, ADR-0022). SCT Inst execution time starts after the bank has checked the file, and SCT Inst files could wait behind large SCT batches. Whether a file is SCT Inst is only known after parsing, in the step that should be prioritised, so the scheme is marked by inbox (SFTP counterpart of DK order type CIP) and work items carry a priority inherited by later steps. Priority orders due items; it does not reserve workers.
The ISO schema accepted non-SEPA content (`PmtMtd` `CHK`, `ChrgBr` `SHAR`, other service levels). Using the DK TVS GBIC_5 as the only schema would replace precise reason codes with `FF01` parser messages, and adding rules by hand per restriction is how the gap arose. So order is: ISO schema, rules, then the TVS only on files that passed everything. *Trade-off:* a file with a rule violation and a TVS-only violation learns of the second after fixing the first. The TVS file is bundled unchanged as `src/main/resources/xsd/pain.001.001.09_GBIC_5.xsd` (SHA-256 `d64da6e1553cf8dd38f98818fe3234fb79daec792d0a69483a1ab8e90dec8b8c`, from `DK-TVS_SEPA_GBIC_5zzglISO_Originale.zip` on ebics.de) and replaced on each GBIC generation.

## Risks / Trade-offs

- [Rationale becomes harder to find than an indexed ADR folder] → `CLAUDE.md` and `openspec/config.yaml` point to this archived design; A-numbers are stable references.
- [Agent-drafted specs miss or misstate a rule] → every requirement is reviewed against its test before archiving; discrepancies found during drafting are listed in tasks.md and resolved before archive.
- [Specs drift from code like the design doc did] → future changes edit specs through OpenSpec deltas; a change without a spec delta for changed behaviour does not pass review.
- [ADR numbers survive in people's heads, commit messages and the runbook] → git history keeps the ADR texts; the runbook and code are repointed in this change.
- [Deleting the design doc loses context that is neither behaviour nor rationale, e.g. the context diagram] → condensed into `config.yaml` context; anything else stays reachable in git history.

## Migration Plan

1. Review and correct the specs against code and tests; resolve the listed discrepancies.
2. Fill `openspec/config.yaml` `context` and create `docs/roadmap.md`.
3. Repoint references in source comments, `CLAUDE.md` and the runbook.
4. Delete `docs/adr/` and `docs/design/`.
5. Run the full build (`mise exec -- ./gradlew build`) and `openspec validate --strict`.
6. Archive the change, which creates `openspec/specs/` and freezes this record.

Rollback: revert the commit; documentation only.
