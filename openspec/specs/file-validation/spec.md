# file-validation Specification

## Purpose

Decide whether a payment file is accepted or rejected as a whole: run the validation stages in a fixed order, collect every violation with its ISO 20022 reason code and location, check group and payment information totals, detect duplicate message and payment information ids, apply the DK technical validation subset as the final gate, and bound validation in time.

## Requirements

### Requirement: Whole-file acceptance or rejection
The system SHALL accept a payment file only when validation finds no violation at all. Any violation MUST reject the entire file; valid parts of a rejected file are never accepted. Every violation MUST carry an ISO 20022 external status reason code, its location in the file and a readable message.

#### Scenario: Valid file is accepted
- **WHEN** a schema-valid `pain.001.001.09` file that breaks no rule is validated
- **THEN** the file is accepted with no violations
- **AND** the outcome carries the file's `MsgId` and the message name `pain.001.001.09`
- **AND** the file ends in status accepted and its status report is scheduled

#### Scenario: One invalid transaction rejects the whole file
- **WHEN** a customer uploads a file in which one transaction has a creditor IBAN with wrong check digits and another an amount in `USD`
- **THEN** the customer receives a status report with group status `RJCT` listing the reason codes `AC03` and `AM03`
- **AND** the file ends in status rejected

### Requirement: Validation stages and their order
The system SHALL validate a single-message file in this order: format detection, the ISO schema of the format, the content rules, the DK technical validation subset, and finally duplicate message id detection. A stage that finds violations MUST end validation of the file: schema errors stop the content rules, and content rule violations stop the DK subset check. Within the content rule stage every rule MUST be evaluated, so all rule violations are reported together.

#### Scenario: All rule violations are collected
- **WHEN** a file has a creditor IBAN with wrong check digits in its first transaction, a currency other than `EUR` in its second transaction and a wrong `GrpHdr/NbOfTxs`
- **THEN** the file is rejected with `AC03` at `PmtInf[1]/CdtTrfTxInf[1]/CdtrAcct/Id/IBAN`, `AM03` at `PmtInf[1]/CdtTrfTxInf[2]/Amt/InstdAmt/@Ccy` and `AM18` at `GrpHdr/NbOfTxs`
- **AND** the outcome still carries the file's `MsgId`

#### Scenario: Rule violations are reported before the DK subset is checked
- **WHEN** a file uses payment method `CHK` (allowed only by the ISO schema) and also declares a wrong control sum
- **THEN** only the `AM10` violations at `GrpHdr/CtrlSum` and `PmtInf[1]/CtrlSum` are reported, and no DK subset violation

### Requirement: Cap on reported violations
The system SHALL list at most 1,000 violations per file. When the content rules find more, the further violations MUST only be counted and summarised in one additional `NARR` entry at `Document` stating how many more violations were not listed. Schema validation MUST stop once 1,000 schema errors are collected and add one `FF01` entry stating that schema validation stopped after 1,000 errors.

#### Scenario: Rule violations beyond the cap are summarised
- **WHEN** a customer uploads a file with 1,200 transactions that each have an invalid creditor IBAN
- **THEN** the status report lists 1,000 `AC03` reasons followed by one `NARR` reason stating that 200 more violations were not listed

#### Scenario: Schema validation stops at the cap
- **WHEN** schema validation with a cap of 2 errors meets a file with three schema errors
- **THEN** two schema errors are reported, followed by one entry stating that validation stopped after 2 errors

### Requirement: Declared number of transactions matches
The system SHALL compare a declared `NbOfTxs` with the number of transactions actually contained, for the group header against all transactions of the file and for each payment information block against its own transactions. A mismatch MUST be reported as `AM18` at `GrpHdr/NbOfTxs` or `PmtInf[n]/NbOfTxs`. An absent `NbOfTxs` in a payment information block is not checked.

#### Scenario: Group header count does not match
- **WHEN** `GrpHdr/NbOfTxs` declares a different number than the transactions in the file
- **THEN** the file is rejected with `AM18` at `GrpHdr/NbOfTxs`

#### Scenario: Matching or absent count passes
- **WHEN** a declared count equals the actual number of transactions, or a payment information block declares no `NbOfTxs`
- **THEN** no `AM18` violation is reported for it

### Requirement: Declared control sum matches
The system SHALL compare a declared `CtrlSum` with the sum of the instructed amounts actually contained, for the group header and for each payment information block. The comparison MUST be numeric, so `150.5` equals `150.50`. A mismatch MUST be reported as `AM10` at `GrpHdr/CtrlSum` or `PmtInf[n]/CtrlSum`. An absent `CtrlSum` is not checked.

#### Scenario: Control sum does not match
- **WHEN** a file whose amounts add up to `150.50` declares `150.51` as control sum in the group header and in its only payment information block
- **THEN** the file is rejected with `AM10` at `GrpHdr/CtrlSum` and `AM10` at `PmtInf[1]/CtrlSum`

#### Scenario: Numerically equal or absent control sum passes
- **WHEN** a declared control sum differs from the actual sum only in trailing zeros, or no control sum is declared
- **THEN** no `AM10` violation is reported

### Requirement: Payment information ids are unique within a file
The system SHALL reject a file in which a `PmtInfId` occurs in more than one payment information block. The violation `DU02` MUST be reported at each repeated occurrence, not at the first one.

#### Scenario: Repeated payment information id
- **WHEN** a file has two payment information blocks with the same `PmtInfId`
- **THEN** the file is rejected with exactly one violation `DU02` at `PmtInf[2]/PmtInfId`

#### Scenario: Distinct payment information ids
- **WHEN** a file has two payment information blocks with different `PmtInfId` values and correct totals
- **THEN** the file is accepted

### Requirement: DK technical validation subset as final gate
The system SHALL validate every `pain.001.001.09` file that passes the ISO schema and every content rule against the DK technical validation subset GBIC_5 of DFÜ-Abkommen Anlage 3 V3.9, bundled unchanged. Each error MUST be reported as `FF01` at `Line n, column m`, with the message prefixed `DK subset GBIC_5: `. The outcome of a file rejected by the subset MUST keep its `MsgId`. Among others, the subset rejects payment methods other than `TRF` and charge bearers other than `SLEV`.

#### Scenario: Cheque order is rejected by the DK subset
- **WHEN** a customer uploads a file with payment method `CHK`, which the ISO schema allows
- **THEN** the file is rejected and every reported reason code is `FF01`

#### Scenario: Charge bearer other than SLEV is rejected by the DK subset
- **WHEN** a file declares charge bearer `DEBT`
- **THEN** the file is rejected with `FF01` violations whose messages start with `DK subset GBIC_5: `
- **AND** the outcome carries the file's `MsgId`

### Requirement: Message ids are unique per submitter among accepted files
The system SHALL register the `MsgId` of every accepted file for its submitter, the authenticated channel identity (e.g. `sftp:<account>`), never an identity taken from the file. A file that passes every other check but whose `MsgId` was already accepted for the same submitter MUST be rejected with `DU01` at `GrpHdr/MsgId`. Rejected files MUST NOT register their `MsgId`, so a corrected file can be resubmitted under the same `MsgId`. The same `MsgId` from different submitters MUST NOT conflict. When several files with the same `MsgId` from one submitter are accepted concurrently, exactly one MUST be accepted.

#### Scenario: Resubmission of an accepted file is rejected
- **WHEN** a customer uploads a valid file, it is accepted, and the same file is uploaded again
- **THEN** the second file is rejected with exactly one violation `DU01` at `GrpHdr/MsgId`
- **AND** the customer's status report for the second file lists only `DU01`

#### Scenario: Corrected resubmission after a rejection is accepted
- **WHEN** a file is rejected for an invalid creditor IBAN and a corrected file with the same `MsgId` is uploaded
- **THEN** the corrected file is accepted

#### Scenario: Message ids are scoped per submitter
- **WHEN** submitters `sftp:customer-a` and `sftp:customer-b` each have a file with the same `MsgId` accepted
- **THEN** both are accepted, and a further file with that `MsgId` from `sftp:customer-a` is rejected as duplicate

#### Scenario: Concurrent acceptance registers a message id once
- **WHEN** eight files of one submitter with the same `MsgId` are accepted at the same moment
- **THEN** exactly one registration succeeds and the others are treated as duplicates

### Requirement: Validation deadline
The system SHALL bound the validation of each file by a deadline (5 minutes by default) covering schema validation, the content rules and the DK subset check. A file whose validation has not finished when the deadline passes MUST be rejected as a whole with a single violation `FF01` at `Document` stating that it could not be validated within the time limit for processing. Such a rejection is final: the file MUST NOT be validated again.

#### Scenario: Validation past the deadline is rejected once
- **WHEN** the validation timeout is 1 ms and a customer uploads a large valid file
- **THEN** the file is rejected with exactly one violation `FF01` mentioning the time limit
- **AND** validation completed on its first attempt without a retry

### Requirement: Technical rejection when validation cannot complete
The system SHALL answer every file. A file whose validation fails unexpectedly, or whose validation attempts are all exhausted, MUST be rejected with a single violation `FF01` at `Document` stating that it could not be processed due to a technical error, with the file id as reference.

#### Scenario: All validation attempts failed
- **WHEN** recording the outcome of a valid file fails on every attempt until the attempts are exhausted
- **THEN** the file is rejected with exactly one violation `FF01` whose message mentions a technical error and contains the file id
- **AND** the file ends in status rejected

#### Scenario: File that kept crashing its worker
- **WHEN** a file's earlier validation attempts all ended without a result and the attempts are exhausted
- **THEN** the file is rejected with `FF01` mentioning a technical error, without being validated again
