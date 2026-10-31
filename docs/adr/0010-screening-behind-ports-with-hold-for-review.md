# 10. Screening behind ports, hits put the file on hold

Date: 2026-10-31

## Status

Proposed — depends on open questions Q2 (sanctions/fraud provider), Q3 (VoP flow), Q5 (four-eyes, UI)

## Context

Sanctions screening, Verification of Payee (VoP) and fraud checks are in scope. Each is a specialised, regulated domain:

- Sanctions screening requires fuzzy name matching against the EU consolidated list, UN lists, German national measures and possibly OFAC. BaFin expects the bank to justify its matching quality.
- VoP (EU Instant Payments Regulation, EPC VoP scheme) must happen **before** the payer authorises the payment. For EBICS uploads, authorisation is the electronic signature, so the VoP result has to reach the customer before signing. Business customers may opt out for bulk submissions.
- Fraud detection rules and models change frequently.

A sanctions or fraud hit is not a format error: it requires a human decision.

## Decision

- Each check is a port in the `screening` module (`SanctionsScreening`, `PayeeVerification`, `FraudCheck`) with one adapter per provider.
- Screening only runs on files that passed validation.
- A sanctions or fraud hit moves the file to `ON_HOLD`; the `review` module records the reviewer's decision (`RELEASED` or `REJECTED`) with reason and identity.
- VoP placement in the flow is decided once Q3 is answered; it may need to run before the file reaches this system's acceptance step.

## Consequences

- Providers can change without touching the pipeline.
- The review queue introduces long-running states; SLAs and escalation need definition.
- Whether we build matching ourselves or integrate a vendor is a separate decision with significant regulatory weight.
