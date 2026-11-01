# credit-transfer-rules Specification

## Purpose

Transaction-level business rules for SEPA credit transfers (SCT) and SEPA Instant credit transfers (SCT Inst) in schema-valid pain.001.001.09 messages, following the EPC SCT and SCT Inst C2PSP Implementation Guidelines 2025 (EPC IG) and DFÜ-Abkommen Anlage 3 v3.9 (DK). Each violation carries an ISO 20022 external status reason code and the location of the offending element (e.g. `PmtInf[1]/CdtTrfTxInf[2]/CdtrAcct/Id/IBAN`), and any violation rejects the file.

## Requirements

### Requirement: SEPA IBANs for debtor and creditor accounts
The system SHALL accept a debtor account (`PmtInf/DbtrAcct/Id/IBAN`) or creditor account (`CdtTrfTxInf/CdtrAcct/Id/IBAN`) only if it is an IBAN in electronic format (upper case letters and digits, no spaces) with the country code of a SEPA scheme country per EPC409-09 v8.0, the length of that country, and valid ISO 7064 MOD 97-10 check digits. An invalid or missing debtor IBAN MUST be reported with `AC02` at `PmtInf[n]/DbtrAcct/Id/IBAN`; an invalid or missing creditor IBAN MUST be reported with `AC03` at `PmtInf[n]/CdtTrfTxInf[m]/CdtrAcct/Id/IBAN`. Lower case letters, which the pain.001 schema allows in the BBAN, MUST be rejected by this rule rather than normalised, because the bank forwards the order unchanged.

#### Scenario: Valid IBANs of SEPA countries are accepted
- **WHEN** a file contains IBANs such as `DE89370400440532013000`, `NL91ABNA0417164300` or `FR1420041010050500013M02606`
- **THEN** no IBAN violation is reported

#### Scenario: Countries that joined SEPA in 2025 are accepted
- **WHEN** a creditor account is a valid IBAN of Albania, Moldova, Montenegro, North Macedonia or Serbia (e.g. `RS35260005601001611379`)
- **THEN** the file is accepted

#### Scenario: Invalid creditor IBAN
- **WHEN** a creditor IBAN has wrong check digits, the wrong length for its country, a non-SEPA country code (e.g. `XX`), or is written in paper format with spaces
- **THEN** the file is rejected with `AC03` at `PmtInf[n]/CdtTrfTxInf[m]/CdtrAcct/Id/IBAN`

#### Scenario: Creditor account not identified by an IBAN
- **WHEN** a creditor account carries no `IBAN`
- **THEN** the file is rejected with `AC03`

#### Scenario: Lower case IBAN that passes the schema
- **WHEN** a creditor IBAN contains lower case letters (e.g. `NL91abna0417164300`)
- **THEN** the file is rejected with `AC03` and not with a schema error `FF01`

#### Scenario: Invalid debtor IBAN
- **WHEN** a payment information block's debtor IBAN is invalid
- **THEN** the file is rejected with `AC02` at `PmtInf[n]/DbtrAcct/Id/IBAN`

### Requirement: Debtor and creditor agent identification
The system SHALL accept a debtor agent identified by `DbtrAgt/FinInstnId/BICFI` or by `DbtrAgt/FinInstnId/Othr/Id` (EPC IG 2.54). A creditor agent SHALL be optional and, when given, MUST consist of `CdtrAgt/FinInstnId/BICFI` only (EPC IG 2.114). A debtor agent with neither identification MUST be reported with `RC01` at `PmtInf[n]/DbtrAgt/FinInstnId`; a creditor agent without `BICFI` or with any further element MUST be reported with `RC01` at `PmtInf[n]/CdtTrfTxInf[m]/CdtrAgt`. The BIC format itself is checked by the schema.

#### Scenario: Debtor agent identified by other identification
- **WHEN** the debtor agent is given as `Othr/Id` `NOTPROVIDED` instead of a BIC
- **THEN** the file is accepted

#### Scenario: Transaction without creditor agent
- **WHEN** a transaction has no `CdtrAgt`
- **THEN** no agent violation is reported

#### Scenario: Creditor agent without BIC
- **WHEN** a creditor agent is identified by `ClrSysMmbId` instead of `BICFI`
- **THEN** the file is rejected with `RC01` at `PmtInf[n]/CdtTrfTxInf[m]/CdtrAgt`

#### Scenario: Creditor agent with details besides the BIC
- **WHEN** a creditor agent carries `BICFI` and further elements
- **THEN** the file is rejected with `RC01`

#### Scenario: Debtor agent without BIC or other identification
- **WHEN** a debtor agent has neither `BICFI` nor `Othr/Id`
- **THEN** the file is rejected with `RC01` at `PmtInf[n]/DbtrAgt/FinInstnId`

### Requirement: Euro currency
The system SHALL accept only the currency `EUR` in `CdtTrfTxInf/Amt/InstdAmt/@Ccy`; any other currency MUST be reported with `AM03` at `PmtInf[n]/CdtTrfTxInf[m]/Amt/InstdAmt/@Ccy`.

#### Scenario: Non-euro amount
- **WHEN** a transaction's instructed amount is in a currency other than `EUR`
- **THEN** the file is rejected with `AM03` at `PmtInf[n]/CdtTrfTxInf[m]/Amt/InstdAmt/@Ccy`

### Requirement: Amount range and precision
The system SHALL accept an instructed amount from 0.01 to 999,999,999.99 euro with at most two significant decimal places (EPC IG 2.95). This range applies to SCT and SCT Inst transactions alike. A zero amount MUST be reported with `AM01`; an amount above 999,999,999.99 MUST be reported with `AM02`; an amount with more than two decimal places MUST be reported with `AM12`; all at `PmtInf[n]/CdtTrfTxInf[m]/Amt/InstdAmt`. Trailing zeros MUST NOT count as decimal places. A non-zero amount breaking both the maximum and the precision MUST be reported with both codes.

#### Scenario: Amounts within the range
- **WHEN** amounts are `0.01`, `50.5`, `100.00`, `999999999.99` or `1.500`
- **THEN** no amount violation is reported

#### Scenario: Zero amount
- **WHEN** an amount is `0` or `0.00`
- **THEN** the file is rejected with `AM01`

#### Scenario: Amount above the SEPA maximum
- **WHEN** an amount exceeds 999,999,999.99
- **THEN** the file is rejected with `AM02`

#### Scenario: More than two decimal places
- **WHEN** an amount is `50.505`, `10.001` or `0.001`
- **THEN** the file is rejected with `AM12`

### Requirement: DK character set
The system SHALL check the text of every element of the message against the DK character set (DK 2.1): the EPC basic Latin set `a-z A-Z 0-9 / - ? : ( ) . , ' +` and space, plus `Ä Ö Ü ä ö ü ß & * $ %`. The identifiers `MsgId`, `PmtInfId`, `InstrId` and `EndToEndId` SHALL allow only the basic Latin set (DK 2.3.1) and MUST NOT start or end with `/` or contain `//` (EPC IG 1.4). A violation MUST be reported with `CH16` at the element's location, once per element, naming the first offending character by code point and position but not the element's text. The bank MUST NOT convert disallowed characters.

#### Scenario: Text in the DK character set
- **WHEN** a creditor name is `Bäckerei Müller & Söhne, Großhandel`
- **THEN** no character set violation is reported

#### Scenario: Character outside the DK set in text
- **WHEN** a creditor name is `Société Générale`
- **THEN** the file is rejected with `CH16` at `PmtInf[n]/CdtTrfTxInf[m]/Cdtr/Nm`, naming `U+00E9` at position 5 without the name

#### Scenario: Other disallowed characters
- **WHEN** a text contains characters such as `ł`, `€`, `@`, `_`, `"`, `<`, a tab or an emoji
- **THEN** the file is rejected with `CH16`, a character outside the Basic Multilingual Plane counting as one code point

#### Scenario: German additional characters in an identifier
- **WHEN** an `EndToEndId` (or `MsgId`, `PmtInfId`, `InstrId`) contains `ü`
- **THEN** the file is rejected with `CH16` at that identifier

#### Scenario: Stray slashes in an identifier
- **WHEN** an identifier is `/E2E-001`, `E2E-001/` or `E2E//001`
- **THEN** the file is rejected with `CH16`

#### Scenario: Slashes in text
- **WHEN** a non-identifier text starts or ends with `/` or contains `//`
- **THEN** no character set violation is reported

### Requirement: Party name length
The system SHALL limit the names of the initiating party (`GrpHdr/InitgPty/Nm`), debtor (`PmtInf/Dbtr/Nm`), creditor (`CdtTrfTxInf/Cdtr/Nm`), ultimate debtor (`PmtInf/UltmtDbtr/Nm`, `CdtTrfTxInf/UltmtDbtr/Nm`) and ultimate creditor (`CdtTrfTxInf/UltmtCdtr/Nm`) to 70 characters, counted as code points, although the ISO schema allows 140. A longer name MUST be reported with `FF01` at the name's location.

#### Scenario: Name of 70 characters
- **WHEN** a name has exactly 70 characters
- **THEN** no name length violation is reported

#### Scenario: Creditor name longer than 70 characters
- **WHEN** a creditor name has more than 70 characters
- **THEN** the file is rejected with `FF01` at `PmtInf[n]/CdtTrfTxInf[m]/Cdtr/Nm`

### Requirement: Remittance information
The system SHALL allow per transaction either one unstructured (`RmtInf/Ustrd`) or one structured (`RmtInf/Strd`) remittance information, or none (EPC IG 2.164–2.166). Both kinds together MUST be reported with `FF01` at `RmtInf`; more than one `Ustrd` MUST be reported with `FF01` at `RmtInf/Ustrd`; more than one `Strd` MUST be reported with `FF01` at `RmtInf/Strd`. Structured remittance information longer than 140 characters, counting the content including its XML tags and attributes written without namespace prefix, MUST be reported with `FF01` at `RmtInf/Strd`.

#### Scenario: One unstructured or one structured remittance information
- **WHEN** a transaction has no remittance information, one `Ustrd`, or one `Strd` of exactly 140 characters including tags
- **THEN** no remittance violation is reported

#### Scenario: Structured and unstructured together
- **WHEN** a transaction has both `Ustrd` and `Strd`
- **THEN** the file is rejected with `FF01` at `PmtInf[n]/CdtTrfTxInf[m]/RmtInf`

#### Scenario: Repeated remittance information
- **WHEN** a transaction has two `Ustrd` or several `Strd`
- **THEN** the file is rejected with `FF01` at `RmtInf/Ustrd` or `RmtInf/Strd` respectively

#### Scenario: Structured remittance information too long
- **WHEN** a `Strd` has 141 characters including tags
- **THEN** the file is rejected with `FF01` at `RmtInf/Strd`

### Requirement: Creditor reference
The system SHALL require a creditor reference (`RmtInf/Strd/CdtrRefInf`), when present, to carry both a type (`Tp`) and a reference (`Ref`), and a coded type (`Tp/CdOrPrtry/Cd`) MUST be `SCOR` (EPC IG 2.169–2.175). A proprietary type SHALL be accepted. ISO 11649 check digits MUST NOT be validated, as EPC IG 2.175 does not require it. A missing type, a coded type other than `SCOR`, or a missing reference MUST each be reported with `FF01` at `CdtrRefInf/Tp`, `CdtrRefInf/Tp/CdOrPrtry/Cd` or `CdtrRefInf/Ref`.

#### Scenario: RF creditor reference with type SCOR
- **WHEN** a creditor reference has type code `SCOR` and reference `RF18539007547034`
- **THEN** no creditor reference violation is reported

#### Scenario: Wrong check digits are not checked
- **WHEN** a creditor reference with type `SCOR` has RF check digits that do not verify
- **THEN** no creditor reference violation is reported

#### Scenario: Proprietary type or no creditor reference
- **WHEN** a creditor reference has a proprietary type, or a `Strd` has no `CdtrRefInf`
- **THEN** no creditor reference violation is reported

#### Scenario: Missing type
- **WHEN** a creditor reference has no `Tp`
- **THEN** the file is rejected with `FF01` at `RmtInf/Strd/CdtrRefInf/Tp`

#### Scenario: Coded type other than SCOR
- **WHEN** a creditor reference's type code is not `SCOR`
- **THEN** the file is rejected with `FF01` at `RmtInf/Strd/CdtrRefInf/Tp/CdOrPrtry/Cd`

#### Scenario: Missing reference
- **WHEN** a creditor reference has no `Ref`
- **THEN** the file is rejected with `FF01` at `RmtInf/Strd/CdtrRefInf/Ref`

### Requirement: Creditor name
The system SHALL require every credit transfer to name its creditor in `CdtTrfTxInf/Cdtr/Nm` (EPC IG 2.117, AT-E001); a missing name MUST be reported with `RR03` at `PmtInf[n]/CdtTrfTxInf[m]/Cdtr/Nm`.

#### Scenario: Creditor without name
- **WHEN** a transaction's `Cdtr` has no `Nm`
- **THEN** the file is rejected with `RR03` at `PmtInf[n]/CdtTrfTxInf[m]/Cdtr/Nm`

### Requirement: Debtor and creditor postal addresses
The system SHALL check the debtor address (`PmtInf/Dbtr/PstlAdr`) and each creditor address (`CdtTrfTxInf/Cdtr/PstlAdr`); addresses of ultimate parties and agents SHALL NOT be checked. An address MUST have at most two `AdrLine`. For a requested execution date (`ReqdExctnDt/Dt`, or the date of `ReqdExctnDt/DtTm`) on or after 22 November 2026, an address MUST be structured or hybrid, i.e. contain `TwnNm` and `Ctry` (EPC IG 1.7, 2.23, 2.118). For an earlier date, an address MUST be structured or hybrid, or unstructured: at least one `AdrLine` and no element besides `AdrLine` and `Ctry`. Violations MUST be reported with `RR02` for the debtor and `RR03` for the creditor, at `PstlAdr` for the address form and at `PstlAdr/AdrLine` for too many lines.

#### Scenario: Structured and hybrid addresses
- **WHEN** an address contains `TwnNm` and `Ctry`, with or without up to two `AdrLine`, for execution before or from 22 November 2026
- **THEN** no address violation is reported

#### Scenario: Unstructured address before the switch
- **WHEN** a creditor address consists only of `AdrLine` elements, with or without `Ctry`, and the requested execution date is 21 November 2026
- **THEN** no address violation is reported

#### Scenario: Unstructured creditor address from the switch on
- **WHEN** a creditor address has no `TwnNm` and the requested execution date is on or after 22 November 2026
- **THEN** the file is rejected with `RR03` at `PmtInf[n]/CdtTrfTxInf[m]/Cdtr/PstlAdr`

#### Scenario: Unstructured debtor address from the switch on
- **WHEN** a debtor address consists only of `AdrLine` and `Ctry` and the requested execution date is after 22 November 2026
- **THEN** the file is rejected with `RR02` at `PmtInf[n]/Dbtr/PstlAdr`

#### Scenario: Neither structured nor unstructured before the switch
- **WHEN** before 22 November 2026 an address combines `AdrLine` with `StrtNm` but lacks `TwnNm` and `Ctry`, or has only `Ctry`
- **THEN** the file is rejected with `RR03` for a creditor address

#### Scenario: More than two address lines
- **WHEN** a debtor address has three `AdrLine`
- **THEN** the file is rejected with `RR02` at `PmtInf[n]/Dbtr/PstlAdr/AdrLine`

#### Scenario: Addresses of ultimate parties and agents
- **WHEN** an ultimate party or an agent carries an unstructured postal address
- **THEN** that address is not checked

### Requirement: Requested execution date of SEPA credit transfers
The system SHALL require the requested execution date (`PmtInf/ReqdExctnDt`) of an SCT payment information block to be today or later and a TARGET business day. "Today" MUST be the calendar date in Europe/Berlin. TARGET is closed on Saturdays, Sundays, 1 January, Good Friday, Easter Monday, 1 May, 25 and 26 December; national holidays do not close it. A date in the past MUST be reported with `DT01` at `PmtInf[n]/ReqdExctnDt`; a future date that is not a TARGET business day MUST be reported with `DT01` at the same location. A date both past and not a business day MUST be reported once, as past.

#### Scenario: Today or a future business day
- **WHEN** the requested execution date is today or a later TARGET business day such as Monday 2 November 2026
- **THEN** no date violation is reported

#### Scenario: Date in the past
- **WHEN** the requested execution date is before today
- **THEN** the file is rejected with `DT01` at `PmtInf[n]/ReqdExctnDt`

#### Scenario: Today is decided in Frankfurt time
- **WHEN** a file dated 2 November 2026 is validated at 23:30 UTC on 2 November 2026, already 3 November in Europe/Berlin
- **THEN** the file is rejected with `DT01`, while at 22:30 UTC it is accepted

#### Scenario: Weekend or TARGET closing day
- **WHEN** the requested execution date is Saturday 7 November 2026 or 25 December 2026
- **THEN** the file is rejected with `DT01`

#### Scenario: National holidays are business days
- **WHEN** the requested execution date is a German holiday that does not close TARGET, such as German Unity Day or Ascension Day, or 24 or 31 December on a weekday
- **THEN** no date violation is reported

### Requirement: Requested execution date of SEPA Instant credit transfers
For an SCT Inst payment information block, the system SHALL treat the requested execution date as the earliest start of execution on any calendar day, a date not in the future meaning immediately (DK 2.2.4.1). It MUST NOT report `DT01` for such a block, whether the date is past, a weekend or a TARGET closing day.

#### Scenario: SEPA Instant with a past Sunday
- **WHEN** an SCT Inst file has the requested execution date Sunday 25 October 2026, which is in the past
- **THEN** the file is accepted

#### Scenario: SEPA Instant on a TARGET closing day
- **WHEN** an SCT Inst block has the requested execution date 25 December 2026
- **THEN** no date violation is reported

### Requirement: Local instrument and SEPA Instant identification
The system SHALL identify a transaction as SCT Inst when its effective local instrument is the code `INST`: the `LclInstrm/Cd` of the transaction's own `PmtTpInf` if it has one, otherwise that of its payment information block (DK 2.2.4.1). A payment information block SHALL be SCT Inst when all its transactions are, SCT when none are, and mixed otherwise. In `PmtTpInf` on either level, a local instrument code other than `INST` MUST be reported with `AG02` at `PmtTpInf/LclInstrm`, and a proprietary local instrument (`LclInstrm/Prtry`) MUST be reported with `AG02` at the same location. SCT carries no local instrument.

#### Scenario: No local instrument or INST
- **WHEN** a `PmtTpInf` has no `LclInstrm` or the code `INST`
- **THEN** no local instrument violation is reported

#### Scenario: Other local instrument code
- **WHEN** a payment information block's local instrument code is `SDCL`
- **THEN** the file is rejected with `AG02` at `PmtInf[n]/PmtTpInf/LclInstrm`

#### Scenario: Proprietary local instrument
- **WHEN** a `PmtTpInf` carries `LclInstrm/Prtry`
- **THEN** the file is rejected with `AG02` at its `LclInstrm`

#### Scenario: SEPA Instant from transaction level
- **WHEN** every transaction of a payment information block carries `PmtTpInf/LclInstrm/Cd` `INST` and the block itself has no `PmtTpInf`
- **THEN** the block is treated as SCT Inst

### Requirement: Service level
The system SHALL accept a service level code (`PmtTpInf/SvcLvl/Cd`, on either level) only if it is `SEPA`, or no service level at all (GBIC_5 TVS usage rule, AT-T001). Any other code, such as `URGP`, MUST be reported with `AG02` at `PmtTpInf/SvcLvl/Cd`.

#### Scenario: No service level or SEPA
- **WHEN** a `PmtTpInf` has no `SvcLvl` or the code `SEPA`
- **THEN** no service level violation is reported

#### Scenario: Urgent payment
- **WHEN** a payment information block's service level code is `URGP`
- **THEN** the file is rejected with `AG02` at `PmtInf[n]/PmtTpInf/SvcLvl/Cd`

### Requirement: One scheme per file
The system SHALL accept a file holding either only SCT or only SCT Inst payment information blocks, as DK separates them into the order types CCT and CIP (DK 2.1). A block of another scheme than the file's first block MUST be reported with `AG02` at `PmtInf[n]`, and a block mixing SCT and SCT Inst transactions MUST be reported with `AG02` at its `PmtInf[n]`.

#### Scenario: Files of one scheme
- **WHEN** all payment information blocks of a file are SCT, or all are SCT Inst
- **THEN** no scheme violation is reported

#### Scenario: SCT block after an SCT Inst block
- **WHEN** the first payment information block is SCT Inst and the second is SCT
- **THEN** the file is rejected with `AG02` at `PmtInf[2]`

#### Scenario: Mixed transactions within one block
- **WHEN** a payment information block contains SCT and SCT Inst transactions
- **THEN** the file is rejected with `AG02` at that `PmtInf[n]`

### Requirement: Content matches the submission channel
The system SHALL accept SCT and SCT Inst submitted as a single message in the regular inbox. A file from the SEPA Instant inbox MUST hold only SCT Inst; an SCT payment information block in it MUST be reported with `AG02` at `PmtInf[n]`. A message split from a DK XML container MUST NOT hold SCT Inst, since DK defines no containers for SCT Inst (DK 2.4); an SCT Inst payment information block in it MUST be reported with `AG02` at `PmtInf[n]`. SCT in a container SHALL be accepted.

#### Scenario: Regular inbox
- **WHEN** a single message with SCT or with SCT Inst arrives in the regular inbox
- **THEN** no submission violation is reported

#### Scenario: SEPA Instant inbox
- **WHEN** an SCT Inst file and an SCT file arrive in the SEPA Instant inbox
- **THEN** the SCT Inst file is accepted and the SCT file is rejected with `AG02` at `PmtInf[1]`

#### Scenario: SEPA Instant in a container
- **WHEN** a message split from a container holds SCT Inst
- **THEN** that message is rejected with `AG02` at `PmtInf[1]`, while the container's other messages are validated on their own

#### Scenario: SEPA credit transfers in a container
- **WHEN** a message split from a container holds SCT
- **THEN** no submission violation is reported

### Requirement: Payment type information on one level
The system SHALL accept payment type information (`PmtTpInf`) on the payment information level or on the transaction level, but not on both within the same payment information block (DK 2.2.4.1). A transaction-level `PmtTpInf` in a block that has its own `PmtTpInf` MUST be reported with `FF01` at `PmtInf[n]/CdtTrfTxInf[m]/PmtTpInf`.

#### Scenario: Payment type information on either level
- **WHEN** one block has `PmtTpInf` only on payment information level and another only on its transactions
- **THEN** no level violation is reported

#### Scenario: Payment type information on both levels
- **WHEN** a payment information block has `PmtTpInf` and its first transaction has one too
- **THEN** the file is rejected with `FF01` at `PmtInf[n]/CdtTrfTxInf[1]/PmtTpInf`
