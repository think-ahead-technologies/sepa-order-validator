# container-split Specification

## Purpose

Recognise DK XML containers (DFÜ-Abkommen Anlage 3, container.nnn.001.GBIC5), check their envelope and hashes, and split them into their credit transfer messages, each validated and answered on its own.

## Requirements

### Requirement: Container recognition
The system SHALL treat a received file as a DK XML container when its root element is in the namespace `urn:conxml:xsd:container.nnn.001.GBIC5`. A file that is not well-formed, contains a DOCTYPE, or has a different root namespace SHALL be validated as a single message instead. Recognition and splitting SHALL happen during the file's validation step.

#### Scenario: A container is recognised by its root namespace
- **WHEN** a file's root element is in the container namespace
- **THEN** it is read as a container

#### Scenario: A plain pain.001 is not a container
- **WHEN** a file's root element is a pain.001 `Document`
- **THEN** it is validated as a single message

#### Scenario: A malformed file is not a container
- **WHEN** a file is not well-formed XML
- **THEN** it is not read as a container

### Requirement: Container envelope check
The system SHALL check the container envelope against the DK container schema and the DK container rules before splitting it. The content of embedded messages SHALL NOT be checked at this point; each message is schema-validated on its own after the split. The system SHALL reject the container as a whole with reason code `FF01`, and split none of its messages, when:
- the envelope violates the container schema;
- it holds messages other than credit transfers (`MsgPain001`);
- an embedded message uses a namespace prefix;
- the container is not encoded in UTF-8.

#### Scenario: A schema-invalid container
- **WHEN** a container lacks a mandatory envelope element such as its container id
- **THEN** it is rejected with `FF01` only, no message is split from it, its status is `REJECTED` and the customer receives one status report for it

#### Scenario: A direct debit container
- **WHEN** a container holds direct debit messages
- **THEN** it is rejected as a whole with `FF01`

#### Scenario: An embedded message with a namespace prefix
- **WHEN** an embedded `Document` uses a namespace prefix
- **THEN** the container is rejected as a whole with `FF01`

#### Scenario: A container not encoded in UTF-8
- **WHEN** a container declares an encoding other than UTF-8
- **THEN** it is rejected as a whole with `FF01`

#### Scenario: Message content is left to message validation
- **WHEN** an embedded message is schema-invalid but the envelope is valid
- **THEN** the container is still split and that message is rejected on its own

### Requirement: Container hash check
When a container gives a hash value for a message, the system SHALL compare it, case-insensitively, with the SHA-256 hash of the message in its Canonical XML 1.0 form (without comments) as it appears within the container. A mismatch SHALL reject the whole container with `FF01` at path `MsgPain001[<n>]/HashValue`, and none of its messages SHALL be split. The hash value SHALL be optional.

#### Scenario: Matching hashes
- **WHEN** every message of a container matches its hash value
- **THEN** the container is split and each message is validated

#### Scenario: Hash value in lower case
- **WHEN** a hash value is given in lower-case hexadecimal and matches
- **THEN** the container is split

#### Scenario: A tampered message
- **WHEN** the content of a message no longer matches its hash value
- **THEN** the container is rejected with exactly one `FF01` violation at `MsgPain001[1]/HashValue` and no message is split from it

### Requirement: Messages become payment files of their own
The system SHALL write each embedded message as a standalone UTF-8 pain.001 document in canonical form, keeping its text and declaring the namespaces it inherits from the container. Each message SHALL become a payment file of its own, in container order, linked to the container and its position (from 1), on the container's channel, submitter and inbox, named `<container name without .xml>-msg<n>.xml`, with status `RECEIVED` and its own validation scheduled, all in the same transaction that ends the container's validation. Splitting the same container again SHALL find the existing message files instead of creating new ones. The container itself SHALL stay stored as received.

#### Scenario: Each message is a standalone schema-valid document
- **WHEN** a container with two messages is split
- **THEN** two pain.001 documents result, each schema-valid on its own, in container order

#### Scenario: Text and inherited namespaces are kept
- **WHEN** a message contains umlauts or escaped characters, or uses a namespace prefix declared on the container
- **THEN** the split message keeps the text unchanged and declares the inherited namespace

#### Scenario: A message is stored once under the container's identity
- **WHEN** the same message position of a container is received twice
- **THEN** the same payment file is returned, named `<container>-msg1.xml`, with the container's channel, submitter and link, and one validation scheduled

#### Scenario: Messages keep the container's inbox
- **WHEN** a container from the SEPA Instant inbox is split
- **THEN** its messages are marked as SEPA Instant orders and as split from a container

### Requirement: Each message is answered on its own
After a successful split the container SHALL end in status `SPLIT`, SHALL get no status report and SHALL have the audit entries `PaymentFileReceived` and `PaymentFileSplit`. Each message SHALL be validated, checked for duplicates, reported and delivered as if it had been submitted as a separate file, so a rejected message does not hold back the others.

#### Scenario: One accepted and one rejected message
- **WHEN** a container holds a valid message and a message with an invalid IBAN
- **THEN** the customer receives two status reports named after `<container>-msg1-` and `<container>-msg2-`, the first `ACCP` and the second `RJCT` with `AC03`, the container's status is `SPLIT` and no report is produced for the container

#### Scenario: SEPA Instant inside a container
- **WHEN** a container holds a SEPA credit transfer message and a SEPA Instant message
- **THEN** the SEPA credit transfer message is accepted and the SEPA Instant message is rejected with `AG02` at `PmtInf[1]`
