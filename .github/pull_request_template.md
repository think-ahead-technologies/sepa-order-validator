<!--
Write as briefly as possible. Bullets, not prose. One line per point.
Write "none" where nothing applies. Delete these comments.
-->

## Ticket

<!-- Ticket id and link, e.g. SEPA-123. "none" only for chores. -->

## What

<!-- What changes and why, 1-3 bullets. Name the OpenSpec change if there is one. -->

-

## Risk

<!--
Tick one. Low/Medium: one line why.
High: short risk assessment (what can break, impact, rollback) - required by POL-8.
-->

- [ ] Low - docs, tests, refactoring without behaviour change
- [ ] Medium - new or changed rule, parsing or report behaviour behind existing tests
- [ ] High - payment acceptance or rejection, intake/SFTP, work queue, DB migration, delivery, dependencies, CI, infrastructure

Why:

## Test intensively

<!-- Paths a reviewer or tester must exercise beyond CI. Concrete inputs, not "test everything". -->

-

## Security deep dive

<!--
Tick what the change touches. Each ticked box needs one line where to look.
-->

- [ ] XML parsing or schema validation (`SecureXml`, XXE, size limits)
- [ ] SFTP, host keys, credentials or secrets
- [ ] Logging of payment data (names, IBANs, amounts)
- [ ] SQL or Flyway migrations
- [ ] New or upgraded dependency, CI or build config
- [ ] New attack surface: endpoint, third-party integration, authentication or authorization
- [ ] None of the above

Where:

## Checks

- [ ] `./gradlew build` passes
- [ ] `./gradlew fullTest` passes (if integration-relevant)
- [ ] Specs in `openspec/specs/` match the behaviour
- [ ] AI-assisted code (label the PR, POL-8)
