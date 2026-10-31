# 2. Modular monolith with Spring Modulith

Date: 2026-10-31

## Status

Accepted

## Context

The system covers intake, validation, mandates, screening, manual review, reporting and audit. These are distinct responsibilities with different rates of change, but they share one file lifecycle and one transactional database. A team of modest size builds and operates it.

Microservices would introduce distributed transactions, network failure modes and operational overhead before we know where the real seams are.

## Decision

We build a single Spring Boot application structured as a modular monolith using Spring Modulith:

- Modules: `intake`, `validation`, `mandates`, `screening`, `review`, `reporting`, `audit`.
- Modules interact through published application events and explicitly exposed APIs only.
- Module boundaries are verified by Spring Modulith tests in the build.
- Inside each module, domain code (model, rules) has no Spring dependency; adapters live at the edge (ports and adapters).

## Consequences

- One deployable, one database, simple local transactions.
- Boundary violations fail the build.
- A module (most likely `screening`) can later be extracted into a service; events become messages.
- All modules scale together; acceptable at expected file volumes.
