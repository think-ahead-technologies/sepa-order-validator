# 9. Integrate with an EBICS bank server instead of implementing EBICS

Date: 2026-10-31

## Status

Proposed — depends on open question Q1 (which EBICS server, which EBICS version)

## Context

EBICS is a certified, cryptographically demanding protocol (key management, electronic signatures, distributed signatures/VEU, segmentation). Banks typically run a vendor EBICS bank server. Customers also submit files via SFTP.

## Decision

- We do not implement the EBICS protocol. We integrate with the bank's EBICS bank server, which hands us authorised files and accepts `pain.002` reports for download.
- The integration point for both EBICS and SFTP is "a file arrived from a customer on a channel", modelled as one `intake` port with two adapters.
- The format reported by EBICS (BTF service/message name for EBICS 3.0, or order types such as `CCT`, `CIP`, `CDD`, `CDB`, `CCC`, `CDC`) is recorded and checked against the detected namespace.
- `pain.002` reports are returned through the channel the file arrived on (EBICS download, e.g. `CRZ`/`CDZ` or BTF equivalent; SFTP outbound directory).

## Consequences

- No protocol-level cryptography in our codebase.
- Our behaviour depends on the vendor's integration interface (file drop, API, MQ) — to be confirmed.
- End-to-end tests for EBICS need a real test instance of the bank server.
