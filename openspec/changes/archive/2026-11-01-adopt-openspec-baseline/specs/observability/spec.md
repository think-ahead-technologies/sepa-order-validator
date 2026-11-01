## Purpose

Give operations structured, file-correlated logs and a minimal set of operational endpoints, without ever exposing payment content from customer files in the log output.

## ADDED Requirements

### Requirement: Structured JSON logs
The system SHALL write its logs to the console as Elastic Common Schema (ECS) JSON, one JSON object per line, with the level in `log.level`, the text in `message` and exception details in `error.*`.

#### Scenario: Log line is ECS JSON
- **WHEN** the application logs a message
- **THEN** the console line is a JSON object with the message in `message` and the level in `log.level`

#### Scenario: Failure with exception
- **WHEN** delivery of a status report fails because the outbound channel is unavailable
- **THEN** a `WARN` line is logged whose `error.message` describes the failed outbound write

### Requirement: File correlation
Every log line written while the system handles a specific payment file (receiving it, and each processing step: validate, report, deliver) SHALL carry that file's id in the field `fileId`. The field SHALL be removed when handling of that file ends, so it never leaks onto lines about another file.

#### Scenario: Lifecycle of a file is traceable
- **WHEN** a file is received, validated and its report delivered
- **THEN** filtering the log on its `fileId` yields a line starting `Received payment file`, a line starting `Validated payment file`, and a line starting `Delivered status report`

#### Scenario: Scope ends with the file
- **WHEN** handling of one file ends
- **THEN** subsequent log lines carry no `fileId` for that file

### Requirement: Lifecycle log lines
The system SHALL log at INFO that a file was received (file name, size and submitter), that it was validated with its outcome (`accepted`, or `rejected` with the distinct reason codes), and that its status report was delivered (report message id and group status).

#### Scenario: Rejection logs reason codes only
- **WHEN** a file is rejected for an invalid IBAN
- **THEN** the validation line reads `Validated payment file: rejected with reason codes [AC03]` and contains no violation message

### Requirement: No payment content in logs
The system SHALL NOT log values taken from the content of a payment file: no names, IBANs, amounts, remittance text, violation messages or parser messages; only ids, reason codes and positions. As a safety net, every string value in the JSON log output, including messages and stack traces, SHALL have anything shaped like an IBAN (electronic or paper format, any letter case) masked, keeping the country code, the check digits and the last four characters visible.

#### Scenario: IBAN from a rejected file never reaches the log
- **WHEN** a file with the invalid IBAN `NL91ABNA0417164301` is processed and rejected
- **THEN** the complete log output does not contain `NL91ABNA0417164301`

#### Scenario: IBAN in a log message is masked
- **WHEN** a log message contains `DE89370400440532013000`
- **THEN** the logged message contains `DE89**************3000` and not the full IBAN

#### Scenario: Paper format and several IBANs
- **WHEN** a value contains an IBAN in groups of four or several IBANs
- **THEN** every IBAN is masked, the paper format without revealing its inner groups, and values without IBANs are left unchanged

### Requirement: Operational endpoints
The system SHALL expose over HTTP only the actuator endpoints `health` (overall, plus `liveness` and `readiness` probes) and `prometheus`. The health endpoint SHALL report its status without component details. No other actuator endpoints and no other HTTP endpoints SHALL be exposed.

#### Scenario: Health without details
- **WHEN** `/actuator/health` is requested on a running instance
- **THEN** it answers 200 with status `UP` and no `components`

#### Scenario: Probes
- **WHEN** `/actuator/health/liveness` and `/actuator/health/readiness` are requested
- **THEN** both report status `UP`

#### Scenario: Other endpoints are not exposed
- **WHEN** `/actuator/env`, `/actuator/beans` or `/actuator/loggers` is requested
- **THEN** the answer is 404

### Requirement: Unanswered files metric
The system SHALL publish the gauge `sepa.intake.files.unanswered` (Prometheus name `sepa_intake_files_unanswered`) counting the files whose customer has had no status report for longer than `sepa.intake.reply-overdue-after`. It SHALL be counted from the database on every scrape, so every instance reports the same value whether or not it is the leader.

#### Scenario: File without reply raises the metric
- **WHEN** a file stays without a delivered status report for longer than the overdue threshold
- **THEN** `sepa_intake_files_unanswered` on `/actuator/prometheus` rises by one, and falls back once the report is delivered

### Requirement: Clean shutdown
Stopping the application SHALL NOT log any line at level ERROR.

#### Scenario: Graceful stop
- **WHEN** a running instance is stopped
- **THEN** none of the logged lines has `log.level` `ERROR`
