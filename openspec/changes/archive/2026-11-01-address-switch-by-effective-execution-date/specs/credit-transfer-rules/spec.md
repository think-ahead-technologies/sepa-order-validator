## ADDED Requirements

### Requirement: Structured debtor and creditor postal addresses
The system SHALL check the debtor address (`PmtInf/Dbtr/PstlAdr`) and each creditor address (`CdtTrfTxInf/Cdtr/PstlAdr`); addresses of ultimate parties and agents SHALL NOT be checked. An address MUST be structured or hybrid, i.e. contain `TwnNm` and `Ctry`, and MUST have at most two `AdrLine` (EPC IG 1.7, 2.23, 2.118; DK TVS GBIC_5, which makes `TwnNm` and `Ctry` mandatory in these addresses). This applies at any requested execution date and to SCT and SCT Inst alike. Violations MUST be reported with `RR02` for the debtor and `RR03` for the creditor, at `PstlAdr` for the address form and at `PstlAdr/AdrLine` for too many lines.

#### Scenario: Structured and hybrid addresses
- **WHEN** an address contains `TwnNm` and `Ctry`, with or without up to two `AdrLine`
- **THEN** no address violation is reported

#### Scenario: Unstructured creditor address
- **WHEN** a creditor address consists only of `AdrLine` elements, with or without `Ctry`, whether the requested execution date is 21 November 2026 or later
- **THEN** the file is rejected with `RR03` at `PmtInf[n]/CdtTrfTxInf[m]/Cdtr/PstlAdr`

#### Scenario: Unstructured debtor address
- **WHEN** a debtor address consists only of `AdrLine` and `Ctry`
- **THEN** the file is rejected with `RR02` at `PmtInf[n]/Dbtr/PstlAdr`

#### Scenario: Town without country
- **WHEN** a creditor address contains `TwnNm` but no `Ctry`
- **THEN** the file is rejected with `RR03` at `PmtInf[n]/CdtTrfTxInf[m]/Cdtr/PstlAdr`

#### Scenario: Past-dated SEPA Instant with an unstructured address
- **WHEN** an SCT Inst block with a requested execution date in the past has a creditor address without `TwnNm`
- **THEN** the file is rejected with `RR03` at `PmtInf[n]/CdtTrfTxInf[m]/Cdtr/PstlAdr` as its only violation, without `DT01` and without `FF01`

#### Scenario: More than two address lines
- **WHEN** a debtor address has three `AdrLine`
- **THEN** the file is rejected with `RR02` at `PmtInf[n]/Dbtr/PstlAdr/AdrLine`

#### Scenario: Addresses of ultimate parties and agents
- **WHEN** an ultimate party or an agent carries an unstructured postal address
- **THEN** that address is not checked

## REMOVED Requirements

### Requirement: Debtor and creditor postal addresses
**Reason**: Its switch on 22 November 2026 allowed unstructured addresses that the DK technical validation subset GBIC_5 always rejects with `FF01`; replaced by "Structured debtor and creditor postal addresses", which requires `TwnNm` and `Ctry` at any date.
**Migration**: None for customers: files with unstructured debtor or creditor addresses stay rejected, now with `RR02`/`RR03` at the address instead of `FF01`.
