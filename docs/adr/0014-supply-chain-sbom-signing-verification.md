# 14. Supply chain: SBOM, signing and verification

Date: 2026-10-31

## Status

Accepted — enforcement mechanism on OpenShift and CI integration details open, see Q10 and Q11

## Context

The validator processes payment orders for a regulated bank. Under DORA (ICT risk management, ICT third-party risk) the bank must know which components run in production, react quickly to newly disclosed vulnerabilities, and prove that what runs in production is exactly what the pipeline built.

The bank stores keys in HashiCorp Vault and runs workloads on OpenShift. Build servers have no direct internet access.

Public keyless signing (Sigstore Fulcio/Rekor) writes signing metadata to a public transparency log, which is not acceptable for internal bank artifacts.

## Decision

### SBOM

- The CycloneDX Gradle plugin generates the application SBOM from the resolved dependency graph on every build.
- Spring Boot embeds the SBOM in the application jar and exposes it via the actuator endpoint `/actuator/sbom` (restricted to internal monitoring access).
- The container image SBOM (OS and runtime layers) is produced during the image build (buildpack-embedded SBOM or syft).
- Both SBOMs are uploaded to OWASP Dependency-Track (or the bank's equivalent) for continuous vulnerability monitoring.

### Signing

- The signed artifact is the **container image**, referenced by digest, never by tag.
- Signing uses cosign with a key held in Vault's Transit secrets engine (`hashivault://<key>`). The private key never leaves Vault.
- Attestations attached to the image:
  - CycloneDX SBOM (`cosign attest --type cyclonedx`),
  - SLSA build provenance (source commit, pipeline run, builder identity).
- Only the CI pipeline may sign. It authenticates to Vault with a short-lived, pipeline-bound identity (JWT/OIDC or Kubernetes auth); developers have no signing permission.

### Verification

- OpenShift admission policy rejects any image for this application that lacks a valid signature from the pipeline key and a valid SBOM attestation.
- The mechanism (OpenShift-native `ClusterImagePolicy`/`ImagePolicy`, Kyverno, or Sigstore policy-controller) depends on the OpenShift version and platform team standards — see Q10.
- Verification failures are logged and alerted.

## Consequences

- Production runs only images built and signed by the pipeline; manual or tampered images are rejected at deployment.
- For every running version we can answer "which libraries are inside?" and "which commit and pipeline built it?" — evidence for audits.
- New CVEs in dependencies are detected for already deployed versions, not only at build time.
- The pipeline depends on Vault availability for releases; Vault outage blocks signing (not running workloads).
- Key rotation in Vault requires updating the verification policy on OpenShift; the procedure must be documented and tested.
- Tooling (cosign, syft) must be available on build agents from internal sources, not downloaded at build time.

## Open questions

- **Q10:** Which OpenShift version, and which admission mechanism does the platform team standardise on? Is Red Hat Trusted Artifact Signer / Trusted Profile Analyzer licensed and preferred?
- **Q11:** Which CI system builds and signs (e.g. OpenShift Pipelines/Tekton with Tekton Chains, GitLab CI, Jenkins)? This determines how pipeline identity authenticates to Vault and how provenance is generated.
