# CLAUDE.md — SEPA Payment Order Validator

## What this is

A Spring Boot modular monolith that validates SEPA payment order files (pain.001 / pain.008, German DK formats) for a real bank, screens them, and answers with a pain.002 status report.

Behaviour: OpenSpec capability specs in `openspec/specs/`; project context in `openspec/config.yaml`. Rationale for the architecture: the `design.md` of archived changes, starting with `openspec/changes/archive/*-adopt-openspec-baseline/design.md` (sections A1-A10); read it before changing architecture. Planned work and open questions: `docs/roadmap.md`. New work starts as an OpenSpec change (`/opsx:propose`).

## Tooling

- Java and Gradle are pinned in `mise.toml`. Run everything through mise:
  - `mise install` — install the pinned JDK and Gradle
  - `mise exec -- ./gradlew build` — compile and run the unit tests (no Spring context, no containers), seconds
  - `mise exec -- ./gradlew fullTest` — all tests including integration and end-to-end; meant for CI (not set up yet, see `docs/roadmap.md`)
- Locally run `build` (unit tests). Run `fullTest` only when a change can affect integration or end-to-end behaviour: Spring wiring or configuration (`application.yaml`), Flyway migrations or SQL, intake and SFTP, the work queue, reporting and delivery, logging, module facades or events, or dependencies. Changes confined to rules or parsing logic that unit tests cover do not need it.
  - `mise exec -- ./gradlew test --tests '*IbanCheckTest'` — single unit test class; use `fullTest --tests ...` for an integration or end-to-end test class
  - `mise exec -- ./gradlew bootTestRun` — run the app locally with PostgreSQL and SFTP from Testcontainers
- Docker must be running: integration and end-to-end tests use Testcontainers (PostgreSQL and an SFTP server).
- The test SFTP server is built from `src/test/resources/sftp/Dockerfile` (alpine + OpenSSH, native on arm64). Do not switch to `atmoz/sftp`: it is amd64-only and its sshd segfaults under emulation on Apple Silicon.
- SFTP host keys are always verified (`sepa.sftp.known-hosts` is mandatory); tests generate known_hosts from the container's keys.
- Gradle dependency verification is on (`gradle/verification-metadata.xml`). After adding or upgrading a dependency: `mise exec -- ./gradlew --write-verification-metadata pgp,sha256 --export-keys --refresh-dependencies build`, then review the diff.

## Architecture rules

- Spring Modulith modules are the direct sub-packages of `dev.thinkahead.sepa`: `intake`, `validation`, `reporting`, `audit`, `processing`. `ModularityTests` fails the build on boundary violations.
- A module's base package is its public API (events, facades). Everything else is package-private or lives in a sub-package.
- Modules talk via public facades. File processing runs as work items on the `processing` work queue (spec `file-processing`); application events only notify. No dependency cycles.
- Database schema changes go through Flyway migrations in `src/main/resources/db/migration` only.
- All XML parsers and schema validators are created via `validation.xml.SecureXml` (spec `xml-security`).

## Conventions

- Test first. Every feature has unit, integration (`*IntegrationTest`) and end-to-end (`*EndToEndTest`) tests. No mocks; use real PostgreSQL and SFTP containers and real-world payment data.
- Every source file starts with two `// ABOUTME:` comment lines.
- Violations carry ISO 20022 external status reason codes (e.g. `AC03`, `AM03`, `FF01`).
- Logs are ECS JSON. Never log values from a payment file (names, IBANs, amounts, violation or parser messages); log ids, reason codes and positions. Processing steps get the file id from their worker (spec `observability`).
- Conventional commits, imperative mood. Never use `--no-verify`.
