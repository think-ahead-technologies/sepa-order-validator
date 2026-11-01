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
