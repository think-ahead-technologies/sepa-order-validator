## Why

The application runs as several instances on OpenShift, but the repository only produces a jar: there is no container image, so nothing that CI tests is the artifact that would be deployed. The roadmap's supply chain section makes the image, referenced by digest, the signed and scanned artifact. Nobody can tell today which libraries and OS packages would ship, or whether any of them have known fixable vulnerabilities. This change builds the image, gives the jar and the image a CycloneDX SBOM, and gates publication on a vulnerability scan. It addresses the SBOM part of roadmap "Supply chain"; signing, Dependency-Track, Q10 (OpenShift admission) and Q11 (system of record, which CI may sign and publish) remain open.

## What Changes

- A `Dockerfile` and an allow-list `.dockerignore` at the repository root build the application image from the Spring Boot jar that the verified Gradle build produced. The base image is an Eclipse Temurin 25 JRE, pinned by tag and digest.
- The image runs the application as a fixed non-root user and also starts under an arbitrary UID with group 0, as OpenShift's restricted security context assigns. Application files are read-only for the runtime user.
- The image serves HTTP (actuator health probes, Prometheus) on port 8080 and is configured only through environment variables and mounted files (database, SFTP, known_hosts). It contains no credentials.
- Every build produces a CycloneDX SBOM of the runtime dependencies. It is embedded in the jar and served at `/actuator/sbom/application`.
- GitHub Actions gets an `image` job after `full-test`. On every run it builds the image, creates an image SBOM (OS packages and Java libraries), and scans it. The job fails on High or Critical findings that have a fixed version; accepted risks are listed in a reviewed `.grype.yaml`, each with a reason and an expiry date. The SBOMs and the scan report are kept as workflow artifacts and summarised in the job summary. On pushes to `main` only, and only if the scan passes, the job pushes the image to `ghcr.io` tagged with the commit SHA and records the image digest. GitLab CI neither builds, scans nor publishes the image until Q11 is decided.
- Docs: `docs/roadmap.md` (supply chain: image, SBOMs and scan gate built on GitHub; Dependency-Track, signing and provenance still planned) and the tooling notes in `CLAUDE.md`.

## Capabilities

### New Capabilities

- `container-image`: the deployable container image. Covers what it contains and how it is built (pinned base, the verified jar only), how it runs (non-root, arbitrary UID, port, configuration, probes) and when CI publishes it (after tests and the vulnerability scan pass).

### Modified Capabilities

- `supply-chain`: adds the requirements "Software bill of materials" (jar and image SBOM, served by the application) and "Vulnerability gate" (scan, failure threshold, ignore file with expiry). The existing requirements on integrity of build inputs are unchanged.

## Impact

- New files: `Dockerfile`, `.dockerignore`, `.grype.yaml`. The image has no automated tests; the GitHub `image` job builds and scans it.
- Build: the CycloneDX Gradle plugin is added through the version catalog, and the dependency verification metadata is regenerated. `bootJar` gets a fixed file name.
- Configuration: `application.yaml` exposes the actuator `sbom` endpoint next to `health` and `prometheus`. It reveals dependency versions to anyone who can reach port 8080, so OpenShift routes must not expose `/actuator`.
- CI: a new `image` job in `.github/workflows/ci.yml` (Syft and Grype as digest-pinned images), with `packages: write` for that job only. `.gitlab-ci.yml` jobs are unchanged: GitLab does not build the image while Q11 is open.
- Docs: `docs/roadmap.md`, `CLAUDE.md`.
