## Context

See proposal.md for motivation. Current state:

- `./gradlew build` produces the Spring Boot jar under Gradle dependency verification (spec `supply-chain`), and `fullTest` runs integration and end-to-end tests against PostgreSQL and a self-built SFTP image via Testcontainers.
- CI pins every image and action by tag and digest or commit SHA. GitLab runs Docker-in-Docker; GitHub uses the runner's Docker.
- The application reads its configuration from Spring properties (`spring.datasource.*`, `sepa.sftp.*`, `sepa.sftp.known-hosts` is mandatory), logs ECS JSON to standard output, and exposes `health` (with probes) and `prometheus`.
- Target platform is OpenShift. Its `restricted-v2` SCC runs containers under a random UID from the namespace range with group 0, and ignores the image's `USER`.

## Goals / Non-Goals

**Goals:**
- One `Dockerfile` that turns the verified jar into a small, non-root, OpenShift-compatible image.
- The GitHub `image` job builds and scans the image, so an image that does not build or fails the scan is never published.
- Publication from `main` to `ghcr.io`, referenced by digest.

- A CycloneDX SBOM for the jar (embedded and served) and for the image, and a vulnerability scan that gates publication.

**Non-Goals:**
- Uploading SBOMs to Dependency-Track, cosign signing, SLSA provenance and admission control. They remain roadmap items and build on this image and its SBOM.
- Scanning on GitLab and a scheduled rescan of `main` (decided with Waldo: GitHub only, per commit).
- Publishing from GitLab (Q11), OpenShift manifests or Helm charts, multi-architecture image builds.
- Tuning beyond a sane heap default (CDS/AOT cache, GC selection).

## Decisions

### D1. Package the jar from the Gradle build instead of building inside Docker

The `Dockerfile` copies `build/libs/payment-order-validator.jar` from the build context. The jar is produced beforehand by `./gradlew bootJar`, which runs under dependency verification.

Alternatives:
- *Multi-stage build running the Gradle wrapper inside `docker build`*: self-contained (`docker build .` works on a clean checkout), but CI compiles twice, the build stage needs a JDK image and its own Gradle cache, and dependency verification then runs in two places. Rejected for cost and duplication.
- *Spring Boot `bootBuildImage` (Paketo buildpacks)*: no Dockerfile to maintain, and it ships an SBOM. But the builder and run images are pulled outside Gradle's verification, the base is opaque, and the result is harder to align with OpenShift's arbitrary UID. Rejected; the decision was made with Waldo.

### D2. Base image: Eclipse Temurin 25 JRE on Ubuntu Noble, pinned by index digest

`eclipse-temurin:25.0.5_8-jre-noble@sha256:…` matches the JDK vendor and version of `mise.toml` and the GitLab CI image. The digest is the multi-architecture index, so the same reference builds natively on arm64 laptops and amd64 runners.

Alternatives:
- *Distroless Java*: smaller attack surface, but no shell for `oc debug`, and Java 25 releases lag. Rejected for now.
- *Red Hat UBI OpenJDK runtime*: native to OpenShift and possibly mandated by the bank. Swapping the `FROM` line would not change the spec, so this stays an open question rather than blocking the change.
- *JDK image*: larger and ships a compiler the runtime does not need. Rejected.

### D3. Layered extraction in a separate stage

A first stage on the same JRE image runs `java -Djarmode=tools -jar payment-order-validator.jar extract --layers` to split the jar into dependencies, spring-boot-loader, snapshot-dependencies and application layers. The final stage copies the layers in that order, so a code-only change rebuilds and pushes only the small application layer. Without `--launcher`, the application layer is a thin `payment-order-validator.jar` whose manifest class path points to `lib/`, started with `java -jar`. Its root, including the embedded SBOM under `META-INF/sbom/`, stays on the classpath as the actuator expects (D10).

Alternative: *copy the fat jar and run `java -jar`*. This is simpler, but every commit then pushes the full ~80 MB dependency set. Rejected; the extraction is a few lines and is the approach Spring Boot documents.

### D4. Non-root user, root-group-owned read-only files

The final stage sets a fixed numeric `USER 10001:0`. Application files are owned by `root:root` and are not writable by user or group 10001, so the runtime user cannot modify them under any UID. Nothing in the application writes to its own directory. The JVM and Tomcat temp files go to `/tmp`, which is world-writable in the base image. A numeric UID lets Kubernetes `runAsNonRoot` verify the user without resolving names.

Alternative: *`chgrp -R 0` with `chmod g=u` (writable by group 0)*, the usual OpenShift recipe for images that write to their own directory. Rejected: the application does not need write access, and read-only application files are the safer default.

### D5. JVM options in the entrypoint

`ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", …]` in exec form, so the JVM is PID 1 and receives SIGTERM directly for the existing clean-shutdown behaviour. Operators can add flags through `JAVA_OPTS`-style mechanisms on OpenShift if needed.

Alternative: *`JAVA_TOOL_OPTIONS` in the image*. Rejected: the JVM prints a non-JSON `Picked up JAVA_TOOL_OPTIONS` line on start, which breaks "ECS JSON only".

### D6. Allow-list `.dockerignore`

`.dockerignore` excludes everything (`*`) and re-includes only the `Dockerfile` and the application jar. A deny-list would silently pick up new local files, such as keys, `.env` files or IDE state.

### D7. No automated image tests

The image has no unit, integration or end-to-end tests, and `fullTest` neither builds nor runs it. Waldo decided this as an explicit exception to the project's three-level test rule. What remains is the GitHub `image` job: `docker build` fails on a broken `Dockerfile`, copying the application SBOM out of the jar fails when it is missing, and the Grype gate checks the image's packages.

Alternative: *unit, integration and end-to-end image tests in `fullTest`* (lint of `Dockerfile` and `.dockerignore`, UID and read-only checks, the app in a container against PostgreSQL and SFTP). Rejected by Waldo.

### D8. GitHub Actions: plain Docker CLI, push after full-test, main only

A new `image` job with `needs: full-test` runs `./gradlew bootJar` and `docker build`, then the SBOM and scan steps (D11, D12). Only after the scan passes, and only when `github.event_name == 'push' && github.ref == 'refs/heads/main'` does it log in to `ghcr.io` with `GITHUB_TOKEN` (`docker login --password-stdin`), push `ghcr.io/<owner>/<repo>:<sha>` (repository lowercased, as the registry requires), and write the `RepoDigest` to `$GITHUB_STEP_SUMMARY`. `packages: write` is granted on this job only; the workflow default stays `contents: read`.

Alternative: *`docker/login-action` and `docker/build-push-action`*. These add third-party actions to the supply chain for what is a few CLI lines, and buildx features (cache export, multi-arch) are not needed yet. Rejected for now; easy to switch when multi-arch or provenance attestations arrive.

No `latest` tag: consumers reference the digest, per the roadmap.

### D9. GitLab CI unchanged

GitLab does not build the image. A build or scan job there would add nothing until GitLab publishes, which waits for Q11. The decision with Waldo: scanning runs on GitHub only.

### D10. Application SBOM: CycloneDX Gradle plugin, embedded and served by Spring Boot

The CycloneDX Gradle plugin (`org.cyclonedx.bom`) is added through the version catalog. When it is applied, the Spring Boot Gradle plugin wires its output into `bootJar` as `META-INF/sbom/application.cdx.json`. The actuator `sbom` endpoint then serves it at `/actuator/sbom/application`, which requires adding `sbom` to `management.endpoints.web.exposure.include`. The plugin is limited to `runtimeClasspath`, so test dependencies stay out. This is what the roadmap planned.

Alternatives:
- *Syft on the jar only*: produces an SBOM, but nothing embeds or serves it, and the jar's SBOM would then differ in origin from the build's resolved graph. Rejected.
- *Do not serve the SBOM over HTTP*: smaller exposure, but loses the roadmap's internal-monitoring use. Rejected with Waldo; mitigated by keeping `/actuator` off OpenShift routes.

### D11. Image SBOM: Syft on a saved image tarball

After `docker build`, the job runs `docker save` to a tarball, and Syft (`anchore/syft` image, pinned by digest) reads it as `docker-archive:` and writes CycloneDX JSON. Syft sees the base image's Debian packages and the jars in the extracted dependency layer.

Alternatives:
- *Syft against the Docker daemon*: needs the Docker socket mounted into the Syft container, which gives a third-party tool root-equivalent access to the runner. Rejected.
- *Image SBOM from the application SBOM plus the base image's published SBOM*: two sources, merged by hand, and the base's SBOM is not always published. Rejected.

### D12. Scanner: Grype on the image SBOM, two runs

Grype (`anchore/grype` image, pinned by digest) scans the image SBOM with the ignore file `.grype.yaml`:

1. A report run without a failure threshold writes the full JSON and table reports, including unfixable and ignored findings. It always runs (`if: always()` for the upload).
2. A gate run uses `--only-fixed --fail-on high` and fails the job on High or Critical findings with a fixed version.

A single gating run is not enough: `--only-fixed` also removes unfixable findings from the output, which would hide them from the report.

Scanning the SBOM rather than the image means the gate checks exactly the SBOM we keep as an artifact.

Alternatives:
- *Trivy*: one tool for SBOM and scan, but larger and covers misconfiguration checks we do not need. Rejected by Waldo for Syft and Grype.
- *OSV-Scanner*: weaker on OS packages; would still need Syft. Rejected.
- *Fail on Critical only, or report only*: rejected by Waldo. High and Critical with a fix are actionable, while unfixable findings would only block releases without a remedy.

### D13. Pinning Syft and Grype

Both tools run as `docker run` of their official images, pinned by tag and index digest like every other CI image. They are not installed with `curl | sh` and not run through third-party GitHub actions. Grype downloads its vulnerability database on every run, so findings are current at scan time. The database is not cached; the download takes seconds and caching would risk a stale gate.

Alternative: *`anchore/sbom-action` and `anchore/scan-action`*. These are more convenient but add two actions to the supply chain, and they install the binaries at run time. Rejected for consistency with D8.

### D14. Ignore file with enforced expiry

`.grype.yaml` holds the `ignore` rules. Grype has no expiry field, so each rule's `reason` must start with `expires YYYY-MM-DD:` followed by the justification. Code review checks the id, the reason format and the expiry date when the file changes; nothing in the build enforces them.

Alternative: *a unit test that parses the file and fails on missing or expired entries*. Rejected by Waldo.

## Risks / Trade-offs

- [`docker build .` on a clean checkout fails without a prior `./gradlew bootJar`] → `CLAUDE.md` documents the two-step command, and the `Dockerfile` header states it.
- [Base image digest goes stale and misses JRE security fixes] → Bumping it is a manual edit together with `mise.toml` and `.gitlab-ci.yml`, as for the JDK today. The Grype gate fails when the stale base has fixable High or Critical findings.
- [`GITHUB_TOKEN` with `packages: write` in a job that runs repository code] → The push steps run only on pushes to `main`. Pull requests from forks get a read-only token anyway.
- [`/actuator/sbom` reveals exact dependency versions to anyone reaching port 8080] → Port 8080 stays cluster-internal; OpenShift routes must not expose `/actuator`. Recorded in the roadmap's operations notes.
- [A new CVE in an unchanged dependency blocks an unrelated merge on `main`] → Intended: fix it by upgrading, or accept it in `.grype.yaml` with a reason and an expiry date. Without a nightly rescan, such CVEs surface only on the next commit (decided with Waldo).
- [Grype database download fails or the vulnerability feed is unavailable] → The job fails and nothing is published; rerun the job. A gate that passes without data would be worse.
- [Grype drops the `reason` field of ignore rules in a later version] → Checked for the pinned v0.121.0, which records `reason` on ignored matches. Recheck on every Grype upgrade.
- [Image regressions (default UID, read-only files, arbitrary UID start, probes, ECS JSON output, `/actuator/sbom`) are not tested] → They surface only when the image is deployed. Accepted with Waldo (D7).
- [An expired `.grype.yaml` acceptance keeps suppressing its finding] → Nothing fails on expiry; it is only noticed when someone reads the file (D14).

## Migration Plan

Additive. No deployment exists yet. Rollback is deleting the job and files; published images stay addressable by digest until removed from `ghcr.io`.

## Open Questions

- Does the bank require Red Hat UBI base images on OpenShift? If so, only the `FROM` lines and their digests change (D2).
