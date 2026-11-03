## ADDED Requirements

### Requirement: OpenTelemetry export
The system SHALL export its logs and traces via OTLP over HTTP/protobuf to an OpenTelemetry collector when a collector endpoint is configured, through the standard variable `OTEL_EXPORTER_OTLP_ENDPOINT` or the corresponding Spring Boot properties. Without a configured endpoint the system SHALL NOT export anything. All traces SHALL be sampled by default. Console logging and `/actuator/prometheus` SHALL stay unchanged by the export.

#### Scenario: Signals reach the collector
- **WHEN** an endpoint is configured, metric export is switched on, and a payment file is received, validated and its report delivered
- **THEN** the collector receives log records, the metric `sepa.processing.step` and spans for the file, each with `service.name` `payment-order-validator`

#### Scenario: No endpoint configured
- **WHEN** no collector endpoint is configured and the application processes a file
- **THEN** no OTLP request is sent and no log line mentions a failed export

#### Scenario: Collector unavailable
- **WHEN** an endpoint is configured but the collector does not answer
- **THEN** file processing completes as without export; only the OpenTelemetry exporters log the failed exports, at level ERROR for spans and logs and WARN for metrics, and no other line is logged at level ERROR

### Requirement: OpenTelemetry metric export
The system SHALL export its metrics via OTLP to the configured collector only when metric export is switched on with the standard variable `OTEL_METRICS_EXPORTER=otlp` or the property `management.otlp.metrics.export.enabled`; metric export SHALL be off by default. Exported timers SHALL use seconds, so their names match `/actuator/prometheus`.

#### Scenario: Metric export off by default
- **WHEN** an endpoint is configured but metric export is not switched on
- **THEN** no metrics are sent to the collector and no log line mentions a failed metric export

#### Scenario: Step timer in seconds
- **WHEN** metric export is switched on and a file is processed
- **THEN** the collector receives `sepa_processing_step_seconds_count`, not a millisecond variant

### Requirement: Telemetry resource attributes
Every exported signal SHALL carry the resource attribute `service.name` with the application name `payment-order-validator`, and further resource attributes from `OTEL_RESOURCE_ATTRIBUTES`.

#### Scenario: Service name on every signal
- **WHEN** logs, spans and metrics are exported
- **THEN** each carries `service.name` `payment-order-validator`

### Requirement: Processing traces
The system SHALL record a span for receiving a payment file and a span for each processing step attempt (validate, report, deliver). Each of these spans SHALL carry the attribute `sepa.file.id` with the file id and, for steps, `sepa.step` with the step name and `sepa.outcome` with `completed`, `retried` or `failed`. Spans SHALL NOT carry values from the content of a payment file.

#### Scenario: Steps of one file are found by file id
- **WHEN** a file is received, validated and its report delivered
- **THEN** searching the traces for `sepa.file.id` equal to its id finds a receive span and spans with `sepa.step` `validate`, `report` and `deliver`, each with `sepa.outcome` `completed`

#### Scenario: Failing step attempt
- **WHEN** a delivery attempt fails because the outbound channel is unavailable and is retried
- **THEN** the span of that attempt has `sepa.outcome` `retried` and error status

### Requirement: Processing step metric
The system SHALL publish the timer `sepa.processing.step` (Prometheus name `sepa_processing_step_seconds`) measuring each processing step attempt, tagged with `sepa.step` (`validate`, `report`, `deliver`) and `sepa.outcome` (`completed`, `retried`, `failed`), the same keys as the spans (Prometheus labels `sepa_step` and `sepa_outcome`). It SHALL NOT be tagged with file ids or other unbounded values.

#### Scenario: Delivered file is measured
- **WHEN** a file is received, validated and its report delivered
- **THEN** `sepa_processing_step_seconds_count` on `/actuator/prometheus` has risen for `sepa_step` `validate`, `report` and `deliver` with `sepa_outcome` `completed`

### Requirement: Log and trace correlation
Every log record written inside a span SHALL carry that span's trace id and span id: exported log records in the OTLP trace context, console lines in the fields `traceId` and `spanId`. Exported log records written while a file is handled SHALL carry the attribute `fileId` like the console lines. Outside a span, neither field SHALL be present.

#### Scenario: From a log line to the trace
- **WHEN** the line starting `Validated payment file` is exported for a file
- **THEN** it carries `fileId` and a trace id, and that trace contains the span with `sepa.step` `validate` for the same file

## MODIFIED Requirements

### Requirement: No payment content in logs
The system SHALL NOT log values taken from the content of a payment file: no names, IBANs, amounts, remittance text, violation messages or parser messages; only ids, reason codes and positions. As a safety net, every string value in the JSON log output, including messages and stack traces, SHALL have anything shaped like an IBAN (electronic or paper format, any letter case) masked, keeping the country code, the check digits and the last four characters visible. The same masking SHALL apply to every string exported via OpenTelemetry: log record bodies and attributes (including exception messages and stack traces), and span names, attributes, status descriptions and event attributes.

#### Scenario: IBAN from a rejected file never reaches the log
- **WHEN** a file with the invalid IBAN `NL91ABNA0417164301` is processed and rejected
- **THEN** the complete log output does not contain `NL91ABNA0417164301`

#### Scenario: IBAN in a log message is masked
- **WHEN** a log message contains `DE89370400440532013000`
- **THEN** the logged message contains `DE89**************3000` and not the full IBAN

#### Scenario: Paper format and several IBANs
- **WHEN** a value contains an IBAN in groups of four or several IBANs
- **THEN** every IBAN is masked, the paper format without revealing its inner groups, and values without IBANs are left unchanged

#### Scenario: IBAN never reaches the collector
- **WHEN** a log message and an exception recorded on a span contain `DE89370400440532013000`
- **THEN** the exported log record and span contain `DE89**************3000` and the collector never receives the full IBAN
