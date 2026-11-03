## Why

Operations will run the validator against a Grafana observability stack (Grafana, Loki, Tempo, Prometheus/kube-prometheus-stack and Grafana Alloy as the collector, being built in the `gitops-workshop` repository). Today the validator only writes ECS JSON to the console and offers `/actuator/prometheus` for scraping; it produces no traces, and nothing ties a log line, a metric and the processing of one file together. The stack's collector accepts OpenTelemetry (OTLP), so one vendor-neutral protocol can carry all three signals from the application.

No roadmap entry is resolved. Roadmap D5 (metric for failed work items) stays open but becomes cheaper: step outcomes are now measured.

## What Changes

- The application exports **logs, metrics and traces via OTLP (HTTP/protobuf)** to an OpenTelemetry collector when a collector endpoint is configured; without an endpoint nothing is exported and the application behaves as today.
- Endpoints come from the standard OpenTelemetry environment variables (`OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_RESOURCE_ATTRIBUTES`, `OTEL_SERVICE_NAME`), which Spring Boot maps to its own properties. Logs and traces need only the endpoint; metric export is off by default and is switched on with the standard `OTEL_METRICS_EXPORTER=otlp`.
- Each processing step (validate, report, deliver) of a payment file runs in a span carrying the file id, the step and the outcome; the same observation produces the timer `sepa.processing.step`. Receiving a file runs in a span too.
- Log records exported via OTLP carry the file id, the trace id and the span id, so Grafana can jump between a file's logs and its traces.
- The IBAN-masking safety net covers every string in exported log records and spans (bodies, attributes, exception messages and stack traces, span event attributes), not only the console JSON.
- Console logs stay ECS JSON and additionally carry `traceId` and `spanId` while a span is active. `/actuator/prometheus` stays.
- Tests run against a real Grafana stack in a container (`grafana/otel-lgtm` via Testcontainers): `bootTestRun` sends to it, so Grafana shows the local application.

Out of scope: deployment files (Helm chart and Kustomize environment variables, Alloy pipeline, Grafana dashboards and alerts); they follow in a separate change once the `gitops-workshop` stack has fixed its endpoints.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `observability`: new requirements "OpenTelemetry export", "Processing traces", "Processing step metric" and "Log and trace correlation"; "No payment content in logs" is extended to every exported log record and span.

## Impact

- Dependencies: `spring-boot-starter-opentelemetry` (managed by the Spring Boot BOM), `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` (not managed by the Spring Boot BOM, pinned in the version catalog), test-only `org.testcontainers:testcontainers-grafana`; verification metadata regenerated.
- Code: an OTLP masking decorator next to `LogMasking`, a logback configuration that keeps the console ECS output and adds the OpenTelemetry appender, observations in `StepWorker` and the SFTP intake.
- Configuration: `application.yaml` (sampling, export switched on by endpoint only).
- Tests: unit tests for masking of log records and spans; an integration test and an end-to-end test against the LGTM container that query Loki, Tempo and Prometheus; the existing `ObservabilityEndToEndTest` keeps passing.
- Operations: a deployment sets `OTEL_EXPORTER_OTLP_ENDPOINT` to the Alloy OTLP HTTP endpoint (`http://alloy.monitoring.svc:4318` in `gitops-workshop`). That Alloy forwards only traces today; its log and metric pipelines, and excluding this application from pod-log tailing, belong to the deployment change. Sending data out of the pod needs an egress path to the collector, which the deployment change provides.
