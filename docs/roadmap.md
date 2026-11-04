# Roadmap

What the SEPA Payment Order Validator does today is specified in [`openspec/specs/`](../openspec/specs/). This page lists what is planned but not built, decisions still to take, and open questions. An entry becomes an OpenSpec change (`/opsx:propose`) when work on it starts; the change's design records the decision and its spec deltas record the behaviour.

## Delivered

1. Walking skeleton: SFTP intake, format detection, schema validation, rules, whole-file `ACCP`/`RJCT`, pain.002, audit.
2. SCT rule catalogue, group checks, duplicate detection by `MsgId`.
3. SCT Inst, including the SEPA Instant inbox with priority processing.
4. DK XML containers split into messages; only current DK message versions accepted.
5. Work queue processing, leader election, structured logging and operational endpoints.
6. DK technical validation subset GBIC_5 as final gate.
7. OpenTelemetry export of logs, traces and metrics via OTLP (`OTEL_EXPORTER_OTLP_ENDPOINT`), a span and the timer `sepa.processing.step` per step attempt.

## Planned

### Remaining duplicate checks

| Check | Reason code | Notes |
|---|---|---|
| `PmtInfId` unique per submitter across files | DU02 | Within a file it is checked already |
| End-to-End id unique within a configurable window | DU04 | Window to be decided |

### SEPA Direct Debit (Core and B2B)

- Accept `pain.008.001.08` only, the current DK version, by the same reasoning that limits credit transfers to `pain.001.001.09`. DK containers with direct debit messages (`MsgPain008`) are rejected today.
- Own mandate store keyed by creditor identifier and mandate id, built from accepted collections (signature date, sequence type, last collection date, amendments); a management API for revocation and correction with every change audited. Existing mandates may need an initial data migration. Mandate data is personal data under GDPR retention.
- Intended rules:

| Rule | Reason code |
|---|---|
| Creditor Identifier valid (country, check digits mod-97 excluding business code) | RC01 / tbd |
| Mandate id present, signature date not in the future | MD01 / tbd |
| Collection date respects lead time (D-1, TARGET business day) | DT01 |
| Sequence type consistent with mandate history (`FRST`/`RCUR`/`OOFF`/`FNAL`) | MD01 / tbd |
| No collection after `FNAL`; no collection on a revoked mandate | MD01 |
| Mandate not expired (36 months without collection) | MD07 |
| Amendment indicator set when mandate id or creditor id changes | MD02 |

Codes marked *tbd* are to be confirmed against the DK specification.

### EBICS bank server integration

The system will not implement EBICS (key management, signatures, distributed signatures, segmentation). It integrates with the bank's EBICS bank server, which hands over authorised files and accepts pain.002 reports for download. EBICS becomes a second intake channel next to SFTP. The order type or BTF service reported by EBICS (`CCT`, `CIP`, `CDD`, `CDB`, ...) is recorded and checked against the detected namespace; `CIP` sets the SEPA Instant mark like the SFTP instant inbox. Reports go back through the channel the file arrived on (`CRZ`/`CDZ` or the BTF equivalent). The EBICS submitter identity must share the duplicate-detection scope with SFTP if the bank wants one scope per customer. If the bank server already unpacks DK containers, the container split is never used. Depends on Q1. End-to-end tests need a real test instance of the bank server.

### Screening and review

Screening runs only on files that pass validation. Each check sits behind its own port, so providers can change without touching the pipeline.

| Check | On hit |
|---|---|
| Sanctions (EU consolidated list, UN, German national; OFAC if applicable) | File goes on hold for manual review |
| Verification of Payee | Result must reach the customer before authorisation (Q3) |
| Fraud | File goes on hold for manual review |

A review module records the reviewer's decision (release or reject) with reason and identity. Screening becomes a work queue step (`SCREEN`) between validation and reporting, with new file statuses for screening and hold. The review queue brings long-running states that need SLAs and escalation. Depends on Q2, Q3, Q5.

### Verification of Payee

Depends on Q3.

### Supply chain

Built today (specs `supply-chain`, `container-image`):

- Dependency and wrapper verification.
- Container image from the `Dockerfile`: the verified jar on a digest-pinned Temurin JRE, non-root and OpenShift arbitrary-UID compatible, built and scanned in the GitHub `image` job.
- CycloneDX SBOM generated on every build, embedded in the jar and served at `/actuator/sbom/application`; image SBOM (Syft) in CI.
- Grype vulnerability gate on the image SBOM: High or Critical findings with a fix fail the job; accepted risks in `.grype.yaml` with reason and expiry date. GitHub Actions pushes the image to `ghcr.io` from `main` only after the gate passes, tagged with the commit SHA and recorded by digest.

Planned:

- Upload of the jar and image SBOMs to Dependency-Track or the bank's equivalent, for monitoring between commits (today a new CVE surfaces only on the next pipeline run).
- The container image is the signed artifact, referenced by digest. cosign signs with a key in Vault Transit that only the CI pipeline may use, and attaches the SBOM and SLSA provenance as attestations.
- OpenShift admission rejects images without a valid signature and SBOM attestation; failures are logged and alerted. Depends on Q10, Q11.

### Operations

- GitLab CI (`.gitlab-ci.yml`) is defined: `build`, then `fullTest` on Docker-in-Docker, for merge requests and branches. It has not run yet: it needs a GitLab project as git remote and a runner that allows privileged services.
- GitHub Actions (`.github/workflows/ci.yml`) runs the same `build` and `fullTest` jobs on push to `main` and on pull requests, on GitHub-hosted runners, followed by the `image` job (SBOMs, vulnerability gate, push to `ghcr.io` on `main`).
- Port 8080 serves health probes, Prometheus and the SBOM: it stays cluster-internal, and OpenShift routes must not expose `/actuator`.
- Kustomize manifests (`deploy/kustomize/`) define base, an example overlay and a local overlay for the `gitops-workshop` cluster; the `Dockerfile` image is built, scanned and pushed to `ghcr.io` by GitHub Actions (no signing yet), and there is no environment overlay, secret delivery from the secrets manager or NetworkPolicy yet. Memory (2 GiB) and CPU requests are estimates, not measured.
- Deployment wiring of the OTLP export: Helm chart and the Kustomize environment overlays set `OTEL_EXPORTER_OTLP_ENDPOINT` (the local overlay already sets `http://alloy.monitoring.svc:4318`), `OTEL_METRICS_EXPORTER=otlp` and `OTEL_RESOURCE_ATTRIBUTES`, plus an egress path to the collector. The `gitops-workshop` Alloy forwards only traces today: it needs OTLP pipelines for logs (to Loki) and metrics (to Prometheus), and must stop tailing this application's pod console, or Loki stores every line twice.
- Management APIs (e.g. re-driving work items, today a database function, see [runbook](operations/redrive-work-items.md)) once management APIs have OAuth2.
- Raw files stored as streams (object storage or PostgreSQL large objects) for flat memory per file, when volumes require it; depends on Q9.
- Helm chart (`deploy/helm/payment-order-validator/`) with example values; no environment values, container image build, secret delivery from the secrets manager or NetworkPolicy yet. Memory (2 GiB) and CPU requests are estimates, not measured.

## Decisions pending

Found when the specs were written from the code. Each is either confirmed as specified or becomes a change.

| # | Behaviour today | Question | Affects |
|---|---|---|---|
| D1 | SCT Inst amounts are capped at 999,999,999.99 (`AM02`) like SCT | The SCT Inst Rulebook 2025 has no scheme maximum. Drop the cap for SCT Inst, and where do customer limits come from (Q15)? | credit-transfer-rules |
| D3 | IBAN rule messages quote the IBAN in the pain.002 `AddtlInf` | Acceptable on the customer's own channel, or mask it? | status-report, credit-transfer-rules |
| D4 | `REPORT` writes no audit entry | Should every step transition be audited, including report creation? | file-processing |
| D5 | A `FAILED` work item only logs an ERROR; the invariant "one open work item per non-terminal file" is not monitored | Add a metric for failed items and an invariant check? `sepa_processing_step_seconds_count{sepa_outcome="failed"}` now counts failed attempts. | file-processing, observability |
| D6 | A repeated delivery renames over an existing report | Keep (identical bytes under the same name), or treat an existing identical report as delivered without writing? | status-report |

## Open questions

| # | Question | Affects |
|---|---|---|
| Q1 | Which EBICS bank server product, and EBICS 3.0 BTF or legacy order types (`CCT`, `CIP`, `CDD`, `CDB`, `CCC`, `CDC`, `CRZ`/`CDZ`)? | EBICS integration, file-intake, status-report, container-split |
| Q2 | Sanctions and fraud: build matching ourselves or integrate an existing vendor or internal service? Which lists? | Screening |
| Q3 | VoP: which provider or routing service, and how does the result reach the customer before EBICS signature? Bulk opt-out handling? | Screening, file-intake |
| Q4 | SDD: creditor side only (`pain.008`), or also debtor side (incoming `pacs.003` and B2B mandates)? | SDD, scope |
| Q5 | Review: four-eyes principle required? UI needed? | Review |
| Q6 | `.003.03` / `.003.02` ended 11/2022; `.001.03` / `.008.001.02` are temporarily usable by bilateral agreement, end date pending (DK format lifecycle 09/2026). Decided not to support them. Do any of our customers still send them? | format-acceptance |
| Q7 | Debtor and creditor addresses must contain `TwnNm` and `Ctry` at any date, as the DK subset GBIC_5 requires (EPC C2PSP IG 2025 from 22 November 2026). Confirm the reason codes RR02 (debtor) / RR03 (creditor) with Compliance | credit-transfer-rules |
| Q9 | Retention periods for raw files and audit data | Persistence, compliance |
| Q10 | OpenShift version and admission mechanism for signature verification; Red Hat Trusted Artifact Signer / Trusted Profile Analyzer available? | Supply chain |
| Q11 | GitLab CI and GitHub Actions both build and test. Which one is the system of record? Which GitLab (gitlab.com or the bank's instance) and runners, and may the pipeline sign with Vault Transit from there? Until decided, only GitHub builds the container image, creates SBOMs, runs the vulnerability gate and publishes the image. | Supply chain |
| Q12 | How long must a `MsgId` stay unique per submitter (forever, or e.g. 90 days)? Does DK prescribe a window? | file-validation |
| Q13 | Maximum accepted file size (default 100 MB) and overdue-reply threshold (default 10 min): confirm with business and operations, size pods accordingly | file-intake |
| Q14 | Characters outside the DK set (e.g. `é`, `ł`) are rejected with CH16. Should they be accepted and converted per EPC217-08 instead, and if so by this system or by downstream payment processing? | credit-transfer-rules |
| Q15 | SCT Inst customer limits (Instant Payments Regulation: payer-set limits, not lower than for SCT): does this system check them, and where do the limits come from? | credit-transfer-rules (see D1) |
