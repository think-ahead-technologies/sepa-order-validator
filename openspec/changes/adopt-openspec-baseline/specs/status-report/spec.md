## Purpose

Answer every validated payment message with an ISO 20022 pain.002 customer payment status report that tells the submitter whether the whole message was accepted or rejected and why, and deliver it back through the channel the file arrived on.

## ADDED Requirements

### Requirement: One status report per validated message
The system SHALL produce exactly one pain.002 status report for every payment file that reaches a validation outcome (accepted or rejected). Processing a file again, retrying report creation or re-driving it SHALL NOT create a second report or a second report message id.

#### Scenario: Accepted file gets one report
- **WHEN** a valid pain.001 file is submitted
- **THEN** exactly one pain.002 report is stored and delivered for it

#### Scenario: Re-driven report creation keeps the existing report
- **WHEN** report creation runs again for a file that already has a report
- **THEN** no second report is created and the stored report keeps its original message id

### Requirement: Report message format
The system SHALL write each report as a schema-valid `pain.002.001.10` document in UTF-8, regardless of the submitted message's version. `GrpHdr/MsgId` SHALL be `STS` followed by 32 hexadecimal characters (35 characters, fitting ISO Max35Text), and `GrpHdr/CreDtTm` SHALL be the creation time with offset, truncated to whole seconds.

#### Scenario: Report validates against the pain.002 schema
- **WHEN** a report is created for an accepted or a rejected file
- **THEN** it validates against the `pain.002.001.10` XSD and its `GrpHdr/MsgId` is at most 35 characters long

#### Scenario: Special characters are escaped
- **WHEN** an original message id or a violation message contains `<`, `&` or `>`
- **THEN** the report is well-formed XML and the text reads back unchanged

### Requirement: Original message references
The report SHALL carry `OrgnlGrpInfAndSts/OrgnlMsgId` with the submitted message's `GrpHdr/MsgId` and `OrgnlMsgNmId` with the message name id of the detected format (e.g. `pain.001.001.09`). When the submitted message could not be identified, both elements SHALL contain `NOTPROVIDED`.

#### Scenario: Identified message
- **WHEN** a pain.001.001.09 file with message id `MSG-2026-10-31-001` is reported
- **THEN** `OrgnlMsgId` is `MSG-2026-10-31-001` and `OrgnlMsgNmId` is `pain.001.001.09`

#### Scenario: Unidentifiable message
- **WHEN** a file is rejected before its message id could be read (e.g. a broken container, a file above the size limit, or a technical error)
- **THEN** `OrgnlMsgId` and `OrgnlMsgNmId` are both `NOTPROVIDED`

### Requirement: Group status and reasons
The report SHALL state `GrpSts` `ACCP` when the message has no violations and `RJCT` otherwise. An accepted report SHALL contain no `StsRsnInf`. A rejected report SHALL contain one group-level `StsRsnInf` per reported violation, with the ISO 20022 external status reason code in `Rsn/Cd`, the violation's location in the file in the first `AddtlInf` and a readable message in the second `AddtlInf`; each `AddtlInf` SHALL be truncated to 105 characters.

#### Scenario: Accepted message
- **WHEN** a valid file is reported
- **THEN** `GrpSts` is `ACCP` and there is no `StsRsnInf`

#### Scenario: Rejected message lists every violation
- **WHEN** a file has an invalid IBAN (`AC03`) and a non-EUR amount (`AM03`)
- **THEN** `GrpSts` is `RJCT` and the report contains one `StsRsnInf` with `Cd` `AC03` and one with `Cd` `AM03`, each with location and message in `AddtlInf`

#### Scenario: Long additional information is truncated
- **WHEN** a violation message is longer than 105 characters
- **THEN** its `AddtlInf` holds the first 105 characters

### Requirement: Reported reasons are capped
The system SHALL report at most 1,000 transaction rule violations individually. Further violations SHALL be summarised in one additional `StsRsnInf` with reason code `NARR` whose message states how many more violations were not listed.

#### Scenario: File with very many errors
- **WHEN** a file contains 1,200 transactions with invalid creditor IBANs
- **THEN** the report contains 1,000 `AC03` reasons followed by one `NARR` reason stating that 200 more violations were not listed

### Requirement: Container messages answered on their own
For a DK XML container that is split into its messages, the system SHALL deliver one report per contained message, referencing that message's own `MsgId`, and SHALL NOT deliver a report for the container itself. A container that cannot be split SHALL be answered with a single rejection report for the container.

#### Scenario: Container with an accepted and a rejected message
- **WHEN** a container holding a valid message and a message with an invalid IBAN is submitted
- **THEN** two reports are delivered, the first with `GrpSts` `ACCP` for the first message's `MsgId`, the second with `GrpSts` `RJCT` and reason `AC03` for the second message's `MsgId`, and no report for the container

#### Scenario: Broken container
- **WHEN** a container whose structure is invalid is submitted
- **THEN** exactly one report is delivered with `GrpSts` `RJCT`, `OrgnlMsgId` `NOTPROVIDED` and only `FF01` reasons

### Requirement: Delivery to the submitter
The system SHALL deliver the report through the channel the file arrived on; SFTP is the only delivery channel. On SFTP the report SHALL appear in the outbound directory only once completely written, named `<base>-<fileId>.pain002.xml`, where `<base>` is the submitted file name without a trailing `.xml` (case-insensitive) and `<fileId>` the file's id. For a message of a container, `<base>` SHALL be the container's base name followed by `-msg<n>`, `<n>` being the message's position in the container from 1. Delivering again SHALL replace the report with identical content under the same name.

#### Scenario: Report name for a single file
- **WHEN** the file `orders.xml` is accepted
- **THEN** the customer finds `orders-<fileId>.pain002.xml` in the outbound directory with the stored report as content

#### Scenario: Report names for container messages
- **WHEN** a container `batch.xml` with two messages is split
- **THEN** the reports are named starting with `batch-msg1-` and `batch-msg2-`

#### Scenario: Re-driven delivery
- **WHEN** delivery of an already delivered report is re-driven
- **THEN** the outbound directory holds one report for the file, byte-identical to the stored report, with the original report message id
