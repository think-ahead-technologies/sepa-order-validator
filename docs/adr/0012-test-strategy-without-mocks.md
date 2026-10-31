# 12. Test-first strategy without mocks

Date: 2026-10-31

## Status

Accepted

## Context

Errors in a payment validator cost real money and regulatory standing. Mock-based tests can pass while the real integration fails.

## Decision

- Test-first: every feature and bug fix starts with a failing test.
- Every feature has unit, integration and end-to-end tests.
- No mocks. Tests use real data and real infrastructure:
  - Unit: rules tested with real-world examples (valid/invalid IBANs, creditor identifiers, DK sample files).
  - Integration: Spring Modulith module tests against real PostgreSQL via Testcontainers.
  - End-to-end: a file dropped on a real SFTP server (Testcontainers) produces the expected `pain.002`; EBICS and screening providers via their real test/sandbox instances.
- Security tests (XXE, entity expansion, oversized files) are part of the suite.
- Test output must be clean; expected errors are asserted, not ignored.

## Consequences

- Slower test runs than pure in-memory tests; mitigated by Testcontainers reuse.
- Sandbox access for EBICS server, sanctions, VoP and fraud providers is a prerequisite for those features.
