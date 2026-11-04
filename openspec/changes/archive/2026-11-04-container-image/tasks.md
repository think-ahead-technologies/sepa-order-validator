## 1. Image definition

- [x] 1.2 Give `bootJar` the fixed file name `payment-order-validator.jar` in `build.gradle.kts`. Verify: `mise exec -- ./gradlew bootJar && ls build/libs/payment-order-validator.jar`
- [x] 1.3 Resolve the multi-arch index digest of `eclipse-temurin:25.0.5_8-jre-noble` (`docker buildx imagetools inspect`). Write `Dockerfile` (ABOUTME header, extract stage with `-Djarmode=tools extract --layers`, final stage copying the layers, `USER 10001:0`, root-owned read-only files, `EXPOSE 8080`, exec-form entrypoint with `-XX:MaxRAMPercentage=75`) and the allow-list `.dockerignore`. Verify: `mise exec -- ./gradlew bootJar && docker build -t payment-order-validator .`

## 2. Image behaviour

- [x] 2.3 Extend `TestcontainersConfiguration` so known_hosts can be written for a network alias (`sftp`, port 22) as well as for the mapped host port, without changing existing callers. Verify: `mise exec -- ./gradlew fullTest --tests '*SftpKeyAuthenticationEndToEndTest'` stays green

## 3. SBOM and vulnerability policy

- [x] 3.2 Check the pinned Grype version's ignore-rule schema for the `reason` field (design D14, risk). Add `.grype.yaml` (ABOUTME header, empty `ignore:` list, comment explaining the expiry format). Verify: read the file
- [x] 3.4 Add the CycloneDX Gradle plugin via `gradle/libs.versions.toml` and apply it in `build.gradle.kts`, limited to `runtimeClasspath`. Regenerate verification metadata: `mise exec -- ./gradlew --write-verification-metadata pgp,sha256 --export-keys --refresh-dependencies build`, then review the diff. Verify: `unzip -l build/libs/payment-order-validator.jar META-INF/sbom/application.cdx.json`
- [x] 3.5 Add `sbom` to `management.endpoints.web.exposure.include` in `application.yaml`. Verify: `mise exec -- ./gradlew build`

## 4. CI

- [x] 4.1 Resolve tag and index digest of `anchore/syft` and `anchore/grype` (`docker buildx imagetools inspect`), and check the latest stable releases.
- [ ] 4.2 Add the `image` job to `.github/workflows/ci.yml`: `needs: full-test`, job-level `permissions: contents: read, packages: write`, same pinned checkout/mise/setup-gradle actions, `./gradlew bootJar`, `docker build`. Then `docker save`, Syft on `docker-archive:` writing `image.cdx.json`, copy of the application SBOM out of the jar, a Grype report run (JSON and table), and a Grype gate run (`--only-fixed --fail-on high`, `-c .grype.yaml`). Upload SBOMs and reports with the pinned `upload-artifact` (`if: always()`), and write a findings count by severity to `$GITHUB_STEP_SUMMARY`. On `push` to `refs/heads/main` only, after the gate: `docker login ghcr.io` with `GITHUB_TOKEN` via stdin, push `ghcr.io/${GITHUB_REPOSITORY,,}:${GITHUB_SHA}`, write the `RepoDigest` to `$GITHUB_STEP_SUMMARY`. Update the file header comment. Verify: `actionlint .github/workflows/ci.yml` if available, otherwise YAML parse. On the first PR run, confirm that `image` builds, scans, uploads the artifacts and skips the push steps.
- [x] 4.3 Prove the gate locally before relying on it. Run the same Syft and Grype commands against the built image and confirm the exit code is 0 for the current image. Run the gate against a deliberately old image (e.g. a pinned `eclipse-temurin` from years ago) and confirm a non-zero exit naming a fixable High or Critical finding. Verify: record both exit codes in the PR description
- [x] 4.4 Leave `.gitlab-ci.yml` jobs unchanged; extend its header comment to say that GitLab does not build the container image and that building, scanning and publishing run on GitHub only while Q11 is open. Verify: `git diff .gitlab-ci.yml` shows comment lines only

## 5. Docs

- [x] 5.1 `docs/roadmap.md`: under "Supply chain", mark as built: the container image (built and scanned in the GitHub `image` job, and published from GitHub `main` to `ghcr.io` by digest), the CycloneDX SBOM embedded in the jar and served at `/actuator/sbom`, the image SBOM, and the Grype gate. Keep the Dependency-Track upload, signing, provenance and admission as planned. Under operations, OpenShift routes must not expose `/actuator`. Add to Q11 that GitLab scanning and publishing are pending. Verify: read the section
- [x] 5.2 `CLAUDE.md` tooling: building the image locally (`mise exec -- ./gradlew bootJar && docker build -t payment-order-validator .`); the JRE base digest must be bumped together with `mise.toml` and `.gitlab-ci.yml`; on a failed scan, upgrade the dependency or base image, or add a `.grype.yaml` entry with `expires YYYY-MM-DD:` and a reason. Verify: read the section

## 6. Verification

- [x] 6.1 Run `mise exec -- ./gradlew build` (unit tests, Spotless, dependency verification) and confirm it is green with clean output
- [x] 6.2 Run `mise exec -- ./gradlew fullTest` (the change affects build wiring, dependencies and configuration) and confirm it is green with clean output
- [x] 6.3 `openspec validate container-image --strict` passes

## 7. Follow-up after removing the image tests

- [x] 7.1 `.gitlab-ci.yml` header (lines 2-3): replace "full-test also builds the container image from the Dockerfile and tests it end to end" with the wording of 4.4. Verify: `git diff .gitlab-ci.yml` shows comment lines only
- [x] 7.2 `docs/roadmap.md` "Built today": replace "tested end to end in `fullTest`" for the container image with "built and scanned in the GitHub `image` job". Verify: read the section
- [x] 7.3 Remove the now-unused `TestcontainersConfiguration.knownHostsFileForAlias` and restore the original `knownHostsFile` (task 2.3 served only the removed end-to-end test). Verify: `mise exec -- ./gradlew fullTest --tests '*SftpKeyAuthenticationEndToEndTest'` stays green
