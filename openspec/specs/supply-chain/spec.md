# supply-chain Specification

## Purpose

Make the build use only dependency artifacts and build tooling whose integrity has been checked against recorded checksums and signatures, so a tampered or substituted artifact fails the build.

## Requirements

### Requirement: Dependency verification
The build SHALL verify every resolved dependency artifact and its metadata (POM, Gradle module metadata) against the recorded verification metadata: by PGP signature from a trusted key where the artifact is signed with one, and by SHA-256 checksum otherwise. The build SHALL fail when an artifact's checksum or signature does not match, or when an artifact has no recorded verification entry.

#### Scenario: Tampered artifact
- **WHEN** a resolved dependency artifact's content differs from its recorded checksum, or its signature does not verify against a trusted key
- **THEN** the build fails with a dependency verification error

#### Scenario: Unrecorded dependency
- **WHEN** a dependency is added or upgraded without regenerating the verification metadata
- **THEN** the build fails because the artifact has no verification entry

### Requirement: Offline signature verification
The build SHALL verify signatures with the public keys stored in the repository's keyring, so verification does not depend on reaching a key server. Keys that are listed as ignored SHALL NOT be trusted; artifacts signed only with them SHALL be verified by checksum.

#### Scenario: No key server reachable
- **WHEN** the build runs without access to a key server
- **THEN** signatures are verified using the keyring checked into the repository

### Requirement: Verified build tool distribution
The Gradle wrapper SHALL download only the pinned Gradle distribution and SHALL verify it against the recorded SHA-256 checksum before use.

#### Scenario: Distribution checksum mismatch
- **WHEN** the downloaded Gradle distribution does not match the recorded SHA-256 checksum
- **THEN** the wrapper refuses to run the build

### Requirement: Software bill of materials
Every build SHALL produce a CycloneDX SBOM of the application's runtime dependencies, excluding test-only dependencies, and SHALL embed it in the application jar. The running application SHALL serve that SBOM at `/actuator/sbom/application`. CI SHALL produce a CycloneDX SBOM of the container image that lists both the operating system packages of the base image and the Java libraries in the image, and SHALL keep it, with the application SBOM, as a build artifact of the run.

#### Scenario: SBOM embedded in the jar
- **WHEN** the application jar is built
- **THEN** it contains a CycloneDX JSON SBOM that lists the runtime dependencies, such as the PostgreSQL JDBC driver, and no test-only dependencies such as Testcontainers

#### Scenario: SBOM served by the running application
- **WHEN** a client requests `/actuator/sbom/application` from the running application
- **THEN** the response is the embedded CycloneDX SBOM

#### Scenario: Image SBOM in CI
- **WHEN** CI builds the container image
- **THEN** the run keeps a CycloneDX SBOM of the image listing the base image's OS packages and the application's Java libraries

### Requirement: Vulnerability gate
CI SHALL scan the image SBOM for known vulnerabilities with a vulnerability database current at scan time. A finding of severity High or Critical for which a fixed version exists SHALL fail the scan. All other findings SHALL be reported without failing. Accepted risks SHALL be listed in a reviewed ignore file in the repository. Each entry SHALL name the vulnerability, a reason and an expiry date; code review checks entries for these and for expiry. The full report SHALL be kept as a build artifact of the run and summarised in the run summary.

#### Scenario: Fixable critical vulnerability
- **WHEN** the image contains a package with a Critical vulnerability for which a fixed version exists
- **THEN** the scan fails and names the vulnerability and the package

#### Scenario: Vulnerability without a fix
- **WHEN** the image contains a package with a High vulnerability for which no fixed version exists
- **THEN** the scan passes and the finding appears in the report

#### Scenario: Accepted risk
- **WHEN** a fixable High vulnerability is listed in the ignore file with a reason and an expiry date in the future
- **THEN** the scan passes and the finding is reported as ignored
