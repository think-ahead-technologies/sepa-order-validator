## Purpose

Protect the bank from hostile XML in customer files: payment files are untrusted input, so every parse and schema validation runs hardened against external entities, DTDs, entity expansion and external resource access. The size limit that keeps oversized files from being parsed is part of file intake.

## ADDED Requirements

### Requirement: Files declaring a DTD are rejected
The system SHALL reject any payment file that contains a document type declaration (DOCTYPE). Entities declared in it MUST NOT be expanded or resolved. Such a file MUST be rejected as a whole with `FF01`.

#### Scenario: External entity is rejected without being resolved
- **WHEN** a file declares `<!DOCTYPE Document [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>` and references `&xxe;` in a `pain.001.001.09` root element
- **THEN** the file is rejected with exactly one violation `FF01`
- **AND** the referenced local file is not read and none of its content appears in any violation or status report

#### Scenario: Entity expansion attack is rejected
- **WHEN** a file declares nested entities that would expand exponentially ("billion laughs") and references them in the document
- **THEN** the file is rejected with a single error naming the DOCTYPE, before any entity is expanded

#### Scenario: Format detection does not resolve entities
- **WHEN** format detection reads a file that declares an external entity
- **THEN** no format is detected and the entity is not resolved

#### Scenario: Attack file end to end
- **WHEN** a customer uploads a file declaring an external entity
- **THEN** the customer receives a status report with group status `RJCT`, original message id `NOTPROVIDED` and the single reason code `FF01`
- **AND** the report contains no content of the referenced local file

### Requirement: No external resources are fetched
The system SHALL NOT fetch any external DTD, schema or other resource while parsing or validating a payment file. All schemas used for validation MUST be bundled with the application, and schema imports MUST be served from bundled copies only. XInclude processing MUST be disabled.

#### Scenario: Validation runs on bundled schemas only
- **WHEN** a `pain.001.001.09` file is validated against the ISO schema and the DK technical validation subset
- **THEN** both schemas are loaded from the application itself and no network or file system location named in the payment file is accessed
