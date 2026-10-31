# 18. Structured logging, file correlation and operational endpoints

Date: 2026-10-31

## Status

Accepted

## Context

The validator runs as several instances on OpenShift. Before this decision it logged plain text with Spring Boot defaults:

- Log lines could not be filtered by field in the cluster's log aggregation.
- Validation, reporting and delivery run asynchronously in module event listeners (ADR-0007). Nothing tied their log lines, or a framework-logged listener failure, to the payment file concerned.
- The design requires IBANs and names to be masked in logs (design §10), but nothing enforced it. Parser messages and exception messages can quote values from a payment file.
- Overdue customer replies were raised only as an ERROR log line. There was no health endpoint for OpenShift probes and no metric to alert on.

The append-only `audit_event` table remains the regulatory record of a file's journey. Logs serve operations, not audit.

## Decision

- **JSON logs.** Logs go to the console in Elastic Common Schema (ECS) JSON, using Spring Boot's built-in structured logging (`logging.structured.format.console: ecs`).
- **File correlation.** Every log line about a payment file carries its id as the MDC field `fileId` (`LogContext`).
  - The poller scopes it per file.
  - Processing workers scope it per work item (ADR-0017).
- **Lifecycle log lines.** INFO lines record that a file was received, validated (with reason codes) and that its status report was delivered.
- **No payment content in logs.**
  - Code never logs values taken from a payment file: no names, IBANs, amounts or remittance text, and no violation messages or parser messages, only reason codes and positions.
  - As a safety net, `LogMasking` masks anything shaped like an IBAN in every string value of the JSON output, stack traces included. It keeps the country code, the check digits and the last four characters.
- **Operational endpoints.** Spring Boot Actuator exposes only `health` (with liveness and readiness probes, no details) and `prometheus`, on the application port. The application has no other HTTP endpoints.
- **Overdue-reply metric.** The gauge `sepa.intake.files.unanswered` counts the files whose customer has had no status report for longer than `sepa.intake.reply-overdue-after`. It is counted in the database on every scrape, so every instance reports the same value whether or not it is the leader.

## Consequences

- Operations can follow one file across instances and modules by filtering on `fileId`, and can alert on `sepa_intake_files_unanswered > 0` instead of parsing log text.
- Developers see JSON on the console locally as well.
- Masking catches IBANs but cannot recognise names or free text. The rule against logging payment content stays a code review item, and tests assert that known IBANs from test files never reach the log output.
- Masking applies only to structured output. Switching back to plain-text logging would also switch masking off.
- The application now embeds a web server (Tomcat). The health and metrics endpoints are unauthenticated. They must stay reachable only inside the cluster (no OpenShift route) until management APIs get OAuth2 (design §10).
- Tomcat 11.0.27 logs `Socket accept failed` (AsynchronousCloseException) at ERROR when an instance shuts down. It is shutdown noise, not a fault. It is tracked as an open issue, not suppressed.
