# Architecture Decision Records

Decisions for the SEPA Payment Order Validator. Format: [Michael Nygard's ADR template](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions). Design overview: [`docs/design/payment-order-validator.md`](../design/payment-order-validator.md).

| ADR | Title | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-modular-monolith-with-spring-modulith.md) | Modular monolith with Spring Modulith | Accepted |
| [0003](0003-java-and-spring-boot-stack.md) | Java 25, Spring Boot 4 and Gradle | Accepted |
| [0004](0004-streaming-xml-with-hardened-parsing.md) | Streaming XML processing with hardened parsing | Accepted |
| [0005](0005-format-detection-and-versioned-rule-sets.md) | Format detection by namespace and versioned rule sets | Accepted, format list superseded by 0019 |
| [0006](0006-whole-file-rejection-collecting-all-violations.md) | Whole-file rejection, collecting all violations | Accepted |
| [0007](0007-asynchronous-processing-with-file-lifecycle.md) | Asynchronous processing with a persisted file lifecycle | Accepted, partly superseded by 0017 |
| [0008](0008-postgresql-with-flyway.md) | PostgreSQL with Flyway | Accepted |
| [0009](0009-integrate-with-ebics-bank-server.md) | Integrate with an EBICS bank server instead of implementing EBICS | Proposed |
| [0010](0010-screening-behind-ports-with-hold-for-review.md) | Screening behind ports, hits put the file on hold | Proposed |
| [0011](0011-own-mandate-store-built-from-collections.md) | Own mandate store built from collections | Accepted |
| [0012](0012-test-strategy-without-mocks.md) | Test-first strategy without mocks | Accepted |
| [0013](0013-pain002-as-customer-status-report.md) | pain.002 as the customer status report | Accepted |
| [0014](0014-supply-chain-sbom-signing-verification.md) | Supply chain: SBOM, signing and verification | Accepted |
| [0015](0015-leader-election-with-postgresql-locks.md) | Leader election with PostgreSQL locks | Accepted |
| [0016](0016-duplicate-detection-by-message-id.md) | Duplicate detection by message id per submitter | Accepted |
| [0017](0017-file-processing-driven-by-persisted-state-with-a-postgresql-work-queue.md) | File processing driven by persisted state with a PostgreSQL work queue | Accepted |
| [0018](0018-structured-logging-and-operational-endpoints.md) | Structured logging, file correlation and operational endpoints | Accepted |
| [0019](0019-accept-only-current-dk-message-versions.md) | Accept only the current DK message versions | Accepted |
| [0020](0020-dk-xml-container-split-into-messages-at-intake.md) | DK XML containers are split into their messages at intake | Accepted |
| [0017](0017-file-processing-driven-by-persisted-state-with-a-postgresql-work-queue.md) | File processing driven by persisted state with a PostgreSQL work queue | Accepted |
