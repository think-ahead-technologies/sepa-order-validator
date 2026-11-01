# format-acceptance Specification

## Purpose

Decide which payment message formats the system accepts: a file is identified by the XML namespace of its root element, only the current DK credit transfer version is accepted, and every accepted file must be valid against the ISO 20022 schema of its format before any content rule runs.

## Requirements

### Requirement: Format detection by root namespace
The system SHALL determine the message format of every single-message payment file from the XML namespace of its root element, before schema validation or any content rule. DK XML containers are recognised and handled before format detection (see `container-split`).

#### Scenario: Credit transfer initiation version 09 is detected
- **WHEN** a file's root element is in the namespace `urn:iso:std:iso:20022:tech:xsd:pain.001.001.09`
- **THEN** the file is validated as a `pain.001.001.09` credit transfer initiation

#### Scenario: Malformed file cannot be identified
- **WHEN** a file is not well-formed XML, so no root element namespace can be read
- **THEN** the file is rejected as a whole with a single violation `FF01` at location `Document` stating that the message format is unsupported or unreadable
- **AND** the outcome carries no original message id and no original message name

### Requirement: Only the current DK credit transfer version is accepted
The system SHALL accept credit transfer files only as `pain.001.001.09` (DFÜ-Abkommen Anlage 3 V3.9). Files in any other namespace, including the predecessor versions `pain.001.001.03` and `pain.001.003.03`, direct debit messages and unknown versions, MUST be rejected as an unsupported format with `FF01` and MUST NOT be schema-validated or checked by content rules.

#### Scenario: Predecessor version is rejected as unsupported
- **WHEN** a file in the namespace of `pain.001.001.03` or `pain.001.003.03` is submitted
- **THEN** the file is rejected with exactly one violation `FF01` stating that the format is unsupported
- **AND** the outcome carries no original message name

#### Scenario: Unknown version is rejected as unsupported
- **WHEN** a file in the namespace `urn:iso:std:iso:20022:tech:xsd:pain.001.001.99` is submitted
- **THEN** the file is rejected with exactly one violation `FF01`
- **AND** the outcome carries neither an original message id nor an original message name

### Requirement: Validation against the ISO schema of the detected format
The system SHALL validate every file of an accepted format against the unchanged ISO 20022 schema of that format (`pain.001.001.09`), bundled with the application. Each schema error MUST be reported as a violation `FF01` located at `Line n, column m` of the file (line and column 0 when the parser cannot determine a position). A file with any schema error MUST be rejected without evaluating content rules, and its outcome MUST carry the detected message name.

#### Scenario: Schema-valid file passes on to the content rules
- **WHEN** a well-formed `pain.001.001.09` file that conforms to the ISO schema is validated
- **THEN** no schema violation is reported and the file is checked by the content rules

#### Scenario: Schema error is reported with its position
- **WHEN** a `pain.001.001.09` file lacks the mandatory `GrpHdr/MsgId` element
- **THEN** the file is rejected with `FF01` violations, each located at a positive line number of the file

#### Scenario: Schema-invalid file skips the content rules
- **WHEN** a file has an invalid payment method code `XYZ` (a schema error) and also an IBAN with wrong check digits (a content rule violation)
- **THEN** only `FF01` violations are reported and no `AC03` violation appears
- **AND** the outcome carries the original message name `pain.001.001.09`

#### Scenario: Schema-invalid file end to end
- **WHEN** a customer uploads a schema-invalid `pain.001.001.09` file
- **THEN** the customer receives a status report rejecting the file whose reason codes are all `FF01`

### Requirement: Schema warnings do not reject a file
The system SHALL treat only schema errors as violations. Schema validator warnings MUST NOT become violations and MUST be logged with their line and column only, never with the warning text.

#### Scenario: Warning is not collected as a violation
- **WHEN** the schema validator issues a warning at line 12, column 7 whose text quotes an IBAN from the file
- **THEN** no violation is recorded
- **AND** the log contains the warning position but not the IBAN
