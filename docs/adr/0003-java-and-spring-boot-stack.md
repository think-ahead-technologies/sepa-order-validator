# 3. Java 25, Spring Boot 4 and Gradle

Date: 2026-10-31

## Status

Accepted

## Context

The team has chosen Spring Boot. We need a supported, long-lived baseline for a banking system.

Builds run on bank-internal CI infrastructure without direct internet access. Dependencies must come from internal repositories, and the supply chain must be verifiable for audits (DORA ICT risk management).

## Decision

- Java 25 (current LTS).
- Spring Boot 4.x with Spring Modulith.
- Gradle with the Kotlin DSL (`build.gradle.kts`), using the Gradle wrapper.
- The wrapper's `distributionUrl` and all dependency and plugin repositories point to the internal Artifactory / Nexus mirror; no direct access to public repositories.
- Gradle dependency verification is enabled (`gradle/verification-metadata.xml` with checksums and signature verification); changes to it are reviewed like code.
- Versions are managed centrally in a version catalog (`gradle/libs.versions.toml`).

## Consequences

- Records, sealed types and pattern matching are available for the domain model (e.g. sealed `Violation` hierarchies, scheme-specific transaction types).
- Virtual threads are available for I/O-bound adapters (SFTP, screening providers).
- Adding or upgrading a dependency requires updating the verification metadata, which makes dependency changes visible in review.
- Builds work identically on developer machines and CI as long as both use the internal mirror.
