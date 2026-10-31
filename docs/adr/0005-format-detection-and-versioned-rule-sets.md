# 5. Format detection by namespace and versioned rule sets

Date: 2026-10-31

## Status

Accepted; the list of accepted formats in the context is superseded by [ADR-0019](0019-accept-only-current-dk-message-versions.md)

## Context

We accept all DK-supported formats: `pain.001.001.03`, `pain.001.001.09`, `pain.001.003.03`, `pain.008.001.02`, `pain.008.001.08`, `pain.008.003.02`, and DK container files. Each has its own XSD and partly different rules.

The EPC publishes new rulebooks yearly (usually effective in November) and the DK specification follows. Around a cutover, files with execution dates on both sides of the change are in flight at the same time.

## Decision

- The format is detected from the root element's XML namespace before any further processing. Unknown namespaces are rejected (`FF01`).
- Validation rules are small, independent classes implementing `ValidationRule<T>`.
- Rules are grouped into `RuleSet`s keyed by (scheme, message version, rulebook version).
- Each rulebook version carries an effective date. The rule set is chosen by the rulebook version effective on the requested execution date (SCT) or collection date (SDD).
- Shared rules (IBAN, amount, character set) are reused across rule sets; version-specific rules are added per set.

## Consequences

- A new rulebook means adding a rule set and an effective date, not editing existing rules.
- Old and new rulebooks run side by side across a cutover.
- The mapping of rules to rule sets must be tested explicitly per version.
