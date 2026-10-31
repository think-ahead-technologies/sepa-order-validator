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

## Consequences

- Memory stays flat regardless of file size.
- Rules that need whole-file knowledge (control sums, in-file duplicates) must accumulate state while streaming.
- Security tests (XXE, entity expansion) are part of the test suite.
