# 22. DK technical validation subset as the final validation gate

Date: 2026-11-01

## Status

Accepted. It refines the schema step of [ADR-0005](0005-format-detection-and-versioned-rule-sets.md) and narrows the "every violation in one round trip" promise of [ADR-0006](0006-whole-file-rejection-collecting-all-violations.md) for violations that only the DK subset finds.

## Context

`pain.001.001.09` files were validated against the ISO 20022 original schema and then against our rules. The ISO schema allows content that is not a SEPA credit transfer, and no rule covered it. A review found files with these contents accepted with `ACCP`:

- `PmtMtd` `CHK` (cheque) or `TRA`, where SEPA allows only `TRF`
- `ChrgBr` `DEBT`, `CRED` or `SHAR`, where SEPA allows only `SLEV`
- `SvcLvl/Cd` other than `SEPA`, e.g. `URGP`

The DK publishes the technical validation subset (TVS) GBIC_5 for Anlage 3 V3.9 on ebics.de (`DK-TVS_SEPA_GBIC_5zzglISO_Originale.zip`, file `pain.001.001.09_GBIC_5.xsd`, same namespace as the ISO schema). It restricts the message to what German banks accept, including payment method, charge bearer, the DK character set, amounts, name lengths and postal addresses.

Using the TVS as the only schema would close the gap, but many of its restrictions overlap with our rules. A schema error stops validation before the rules run, so customers would get `FF01` with a line, a column and a parser message, often twice per error, instead of the precise reason codes the rules give today (`AM01`, `AM12`, `CH16`, `RC01`, `RR03`). Adding rules by hand for each TVS restriction keeps the codes, but it is how the gap arose.

## Decision

- Validation runs in this order: the ISO schema (`FF01`), the rules (their reason codes), then the DK TVS schema (`FF01`).
- The TVS check runs only on files that pass the ISO schema and every rule. A file the TVS rejects gets `FF01` violations at `Line n, column m`, with the parser message prefixed `DK subset GBIC_5: `.
- The TVS file is bundled unchanged as `src/main/resources/xsd/pain.001.001.09_GBIC_5.xsd` (SHA-256 `d64da6e1553cf8dd38f98818fe3234fb79daec792d0a69483a1ab8e90dec8b8c`). It has no imports and is compiled through `SecureXml` like every other schema (ADR-0004).
- The TVS restricts `SvcLvl/Cd` only by length and documents `SEPA` as a usage rule, so a rule rejects other service levels with `AG02`, like other local instruments.

## Consequences

- No file that breaks the DK subset is accepted, whatever our rules cover.
- Violations the rules know keep their precise reason codes and paths.
- A file with both a rule violation and a TVS-only violation reports only the rule violation; the customer learns of the TVS violation after correcting the file. This is the price of keeping precise codes.
- An accepted file is parsed once more. The TVS check runs under the same validation deadline.
- On each new GBIC generation, the TVS file is replaced from ebics.de and its hash updated here; rules made redundant by the TVS can then be reviewed.
