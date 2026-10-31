# 4. Streaming XML processing with hardened parsing

Date: 2026-10-31

## Status

Accepted

## Context

Customer files can contain 100,000+ transactions. Loading a whole document into memory (DOM, JAXB unmarshalling of the root) makes memory use proportional to file size.

The files come from outside the bank. XML parsers are a classic attack surface: XXE (external entity injection), billion-laughs entity expansion, DTD retrieval.

## Decision

- Validate each file against the XSD of its detected format using JAXP `SchemaFactory` / `Validator` over a streaming source.
- Parse with StAX, emitting one transaction at a time to the rule engine; group-level totals are accumulated while streaming.
- All parser and schema factories are created through a single factory class that:
  - disables DTDs and external entities,
  - sets `XMLConstants.FEATURE_SECURE_PROCESSING`,
  - restricts `ACCESS_EXTERNAL_DTD` and `ACCESS_EXTERNAL_SCHEMA` to none,
  - bundles all XSDs on the classpath.
- No other code creates XML parsers.
- Files above `sepa.intake.max-file-size` (default 100 MB) are not stored or parsed: intake streams them through SHA-256 only, records size and hash, and they are rejected with `FF01`.
- At most 1,000 violations are reported per file; schema validation stops at that point, and further rule violations are only counted and summarised in one `NARR` entry.

## Consequences

- Parsing and rule evaluation hold one transaction at a time. The raw file, however, is held in memory as a whole while it is read from the channel, stored in a PostgreSQL `BYTEA` column and loaded for validation. Memory use per file is therefore bounded by the maximum file size, not flat; pods must be sized for the configured limit times the number of files processed concurrently.
- Truly flat memory requires storing raw files as streams (object storage or PostgreSQL large objects, see ADR-0008); this is deferred until file volumes require it.
- Rules that need whole-file knowledge (control sums, in-file duplicates) must accumulate state while streaming.
- Security tests (XXE, entity expansion), oversized files and error caps are part of the test suite.
