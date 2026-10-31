# CLAUDE.md — SEPA Payment Order Validator

## What this is

A Spring Boot modular monolith that validates SEPA payment order files (pain.001 / pain.008, German DK formats) for a real bank, screens them, and answers with a pain.002 status report.

Design: `docs/design/payment-order-validator.md`. Decisions: `docs/adr/` (read the ADR index before changing architecture).

## Tooling

- Java and Gradle are pinned in `mise.toml`. Run everything through mise:
  - `mise install` — install the pinned JDK and Gradle
  - `mise exec -- ./gradlew build` — compile and run all tests
  - `mise exec -- ./gradlew test --tests '*IbanCheckTest'` — single test class
  - `mise exec -- ./gradlew bootTestRun` — run the app locally with PostgreSQL and SFTP from Testcontainers
- Docker must be running: integration and end-to-end tests use Testcontainers (PostgreSQL and an SFTP server).
- The test SFTP server is built from `src/test/resources/sftp/Dockerfile` (alpine + OpenSSH, native on arm64). Do not switch to `atmoz/sftp`: it is amd64-only and its sshd segfaults under emulation on Apple Silicon.
- SFTP host keys are always verified (`sepa.sftp.known-hosts` is mandatory); tests generate known_hosts from the container's keys.
- Gradle dependency verification is on (`gradle/verification-metadata.xml`). After adding or upgrading a dependency: `mise exec -- ./gradlew --write-verification-metadata pgp,sha256 --export-keys --refresh-dependencies build`, then review the diff.

## Architecture rules

- Spring Modulith modules are the direct sub-packages of `dev.thinkahead.sepa`: `intake`, `validation`, `reporting`, `audit`. `ModularityTests` fails the build on boundary violations.
- A module's base package is its public API (events, facades). Everything else is package-private or lives in a sub-package.
- Modules talk via application events (`@ApplicationModuleListener`) and public facades. No dependency cycles.
- Database schema changes go through Flyway migrations in `src/main/resources/db/migration` only.
- All XML parsers and schema validators are created via `validation.xml.SecureXml` (ADR-0004).

## Conventions

- Test first. Every feature has unit, integration (`*IntegrationTest`) and end-to-end (`*EndToEndTest`) tests. No mocks; use real PostgreSQL and SFTP containers and real-world payment data.
- Every source file starts with two `// ABOUTME:` comment lines.
- Violations carry ISO 20022 external status reason codes (e.g. `AC03`, `AM03`, `FF01`).
- Logs are ECS JSON. Never log values from a payment file (names, IBANs, amounts, violation or parser messages); log ids, reason codes and positions. Processing steps get the file id from their worker; module event listeners call `LogContext.setFile(fileId)` first (ADR-0018).
- Conventional commits, imperative mood. Never use `--no-verify`.
