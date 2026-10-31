# 1. Record architecture decisions

Date: 2026-10-31

## Status

Accepted

## Context

The validator is a regulated banking system. Auditors, new team members and our future selves need to understand why the system is built the way it is.

## Decision

We record significant architecture decisions as ADRs in `docs/adr/`, numbered sequentially, using the Nygard format (Context, Decision, Consequences). Superseded ADRs are kept and marked as superseded.

## Consequences

- Decisions are reviewable in pull requests alongside the code.
- Every significant change of direction requires a new ADR.
