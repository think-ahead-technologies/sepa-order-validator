## MODIFIED Requirements

### Requirement: Validation stages and their order
The system SHALL validate a single-message file in this order: format detection, the ISO schema of the format, the content rules, the DK technical validation subset, and finally detection of duplicate message ids and then duplicate payment information ids across files. A stage that finds violations MUST end validation of the file: schema errors stop the content rules, content rule violations stop the DK subset check, and a duplicate message id stops the payment information id check. Within the content rule stage every rule MUST be evaluated, so all rule violations are reported together.

#### Scenario: All rule violations are collected
- **WHEN** a file has a creditor IBAN with wrong check digits in its first transaction, a currency other than `EUR` in its second transaction and a wrong `GrpHdr/NbOfTxs`
- **THEN** the file is rejected with `AC03` at `PmtInf[1]/CdtTrfTxInf[1]/CdtrAcct/Id/IBAN`, `AM03` at `PmtInf[1]/CdtTrfTxInf[2]/Amt/InstdAmt/@Ccy` and `AM18` at `GrpHdr/NbOfTxs`
- **AND** the outcome still carries the file's `MsgId`

#### Scenario: Rule violations are reported before the DK subset is checked
- **WHEN** a file uses payment method `CHK` (allowed only by the ISO schema) and also declares a wrong control sum
- **THEN** only the `AM10` violations at `GrpHdr/CtrlSum` and `PmtInf[1]/CtrlSum` are reported, and no DK subset violation

## ADDED Requirements

### Requirement: Payment information ids are unique per submitter among accepted files
The system SHALL register every `PmtInfId` of an accepted file for its submitter, the authenticated channel identity (e.g. `sftp:<account>`), never an identity taken from the file. The `PmtInfId` identifies a payment information block (DK Anlage 3 V3.9, pain.001.001.09 field `PmtInfId`). A file that passes every other check, including the message id check, but contains a `PmtInfId` that was already registered for the same submitter MUST be rejected with `DU02` (ISO 20022 external status reason DuplicatePaymentInformationID) at `PmtInf[n]/PmtInfId` of every payment information block whose id was already registered. The violation message MAY quote the id. Logs MUST NOT contain it. A rejected file MUST NOT register any of its `PmtInfId` values or its `MsgId`, so a corrected file can be resubmitted with the same ids. The same `PmtInfId` from different submitters MUST NOT conflict. When several files from one submitter that share a `PmtInfId` are accepted concurrently, exactly one MUST be accepted. Each message split from a DK container counts as a file of the container's submitter. Registered ids are kept without expiry, like message ids. The DK technical validation subset GBIC_5 restricts the length and character set of `PmtInfId` but not its uniqueness, so this check adds to the subset and never repeats one of its errors.

#### Scenario: Payment information id reused in a later file is rejected
- **WHEN** submitter `sftp:customer-a` has a file with `PmtInfId` `BATCH-1` accepted and then uploads a valid file with a different `MsgId` whose second payment information block has `PmtInfId` `BATCH-1`
- **THEN** the second file is rejected with exactly one violation `DU02` at `PmtInf[2]/PmtInfId`
- **AND** the customer's status report for the second file has group status `RJCT` and lists only `DU02`

#### Scenario: Every reused id is reported
- **WHEN** an accepted file of one submitter had the payment information ids `A` and `B`, and a later file of that submitter has the payment information ids `A`, `C` and `B`
- **THEN** the later file is rejected with `DU02` at `PmtInf[1]/PmtInfId` and at `PmtInf[3]/PmtInfId`, and none at `PmtInf[2]/PmtInfId`

#### Scenario: Resubmission of an accepted file reports only the duplicate message id
- **WHEN** a customer uploads a valid file, it is accepted, and the same file is uploaded again
- **THEN** the second file is rejected with exactly one violation `DU01` at `GrpHdr/MsgId` and no `DU02`

#### Scenario: A file rejected for a reused id registers nothing
- **WHEN** a file is rejected with `DU02` and a corrected file with the same `MsgId` and fresh payment information ids is uploaded
- **THEN** the corrected file is accepted

#### Scenario: Corrected resubmission after a rejection is accepted
- **WHEN** a file is rejected for an invalid creditor IBAN and a corrected file with the same `PmtInfId` values is uploaded
- **THEN** the corrected file is accepted

#### Scenario: Payment information ids are scoped per submitter
- **WHEN** submitters `sftp:customer-a` and `sftp:customer-b` each have a file with `PmtInfId` `BATCH-1` accepted
- **THEN** both are accepted, and a further file with `PmtInfId` `BATCH-1` from `sftp:customer-a` is rejected with `DU02`

#### Scenario: Messages of one container share the submitter's scope
- **WHEN** a DK container holds two messages with different `MsgId` values that both contain `PmtInfId` `BATCH-1`
- **THEN** exactly one message is accepted and the other is rejected with `DU02`

#### Scenario: Concurrent acceptance registers a payment information id once
- **WHEN** eight files of one submitter with different `MsgId` values that all contain `PmtInfId` `BATCH-1` are accepted at the same moment
- **THEN** exactly one file is accepted and the others are rejected with `DU02`
- **AND** the `MsgId` values of the rejected files are not registered
