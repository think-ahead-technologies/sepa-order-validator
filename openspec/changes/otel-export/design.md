## Context

See proposal.md for why. Observed state of the application:

- Logs: Spring Boot structured logging writes ECS JSON to the console (`logging.structured.format.console: ecs`); `LogMasking` masks IBANs in every JSON string value. `LogContext` puts `fileId` into the MDC while a file is handled; `StepWorker` runs each step attempt on its own virtual thread and sets the MDC there.
- Metrics: actuator with `micrometer-registry-prometheus`; `/actuator/prometheus` exposes the gauge `sepa.intake.files.unanswered`. Nothing measures processing steps.
- Traces: none. The only HTTP traffic is actuator scrapes and probes; the real work runs on the work queue workers and the SFTP poller, which no auto-instrumentation covers.
- Target stack (`gitops-workshop`, `main` at 87091a7, 2026-11-03): Flux HelmReleases for kube-prometheus-stack (with Grafana), Loki, Tempo and Grafana Alloy in namespace `monitoring`. Alloy listens for OTLP at `alloy.monitoring.svc:4317` (gRPC) and `:4318` (HTTP) but forwards only traces (to Tempo). Logs reach Loki by Alloy tailing every pod's console; metrics reach Prometheus only by scraping. OTLP logs and metrics from this application have no pipeline there yet.

Spring Boot 4.1 (reference documentation, checked 2026-11-03): `spring-boot-starter-opentelemetry` brings the OpenTelemetry SDK, the Micrometer OTLP registry and the Micrometer tracing bridge. Properties `management.opentelemetry.tracing.export.otlp.*`, `management.opentelemetry.logging.export.otlp.*` and `management.otlp.metrics.export.*`; transport defaults to `http`; `management.opentelemetry.map-environment-variables` (default `true`) maps the standard `OTEL_*` variables. Log export needs the OpenTelemetry Logback appender, which Spring Boot does not ship and does not install. Default sampling probability is 0.1.

## Goals / Non-Goals

**Goals:**
- One protocol (OTLP) for logs, metrics and traces, configured by the standard OpenTelemetry variables.
- A file's logs, traces and step metrics can be found by its file id and linked to each other in Grafana.
- The no-payment-content guarantee holds for every exported byte.
- Tests against a real Grafana stack, no mocks.

**Non-Goals:**
- Deployment files, Alloy pipelines, Grafana dashboards and alerts.
- One trace spanning all steps of a file (see decisions).
- JDBC, SFTP or HTTP client spans.

## Decisions

**D1. Spring Boot's OpenTelemetry starter, not the OpenTelemetry Java agent or the OpenTelemetry Spring Boot starter.** Configuration stays in Spring Boot properties, versions in the Spring Boot BOM, and Micrometer observations feed both metrics and traces.
*Alternatives:* the OpenTelemetry Java agent auto-instruments JDBC, HTTP and more, but is a bytecode agent outside Gradle dependency verification, changes the container start command and would not instrument the work queue anyway; the `io.opentelemetry.instrumentation` Spring Boot starter duplicates what Spring Boot 4 provides and competes with Micrometer.

**D2. All three signals via OTLP over HTTP/protobuf, `/actuator/prometheus` kept.** The user chose OTLP for everything. HTTP/protobuf is the Spring Boot default, works through HTTP proxies and needs no gRPC dependency. The Prometheus endpoint stays because the `observability` spec requires it and existing tests and the planned deployment notes rely on it; a deployment collects metrics either by push or by scrape, never both, to avoid duplicate series. OTLP metrics use seconds as base time unit (`management.otlp.metrics.export.base-time-unit`), so timers have the same names (`sepa_processing_step_seconds_*`) whichever way they are collected.
*Alternatives:* remove the Prometheus endpoint (breaking the spec and scrape-based setups for no gain); gRPC transport (extra dependency, no benefit for this volume); logs only from the console via Alloy tailing pods (rejected by the user in favour of all-OTLP; it would also lose structured trace context unless parsed).

**D3. Logs and traces export when an endpoint is configured; metrics additionally need `OTEL_METRICS_EXPORTER=otlp`.** Spring Boot exports traces and logs only when their endpoint is set. Its OTLP metrics registry has no such condition: it is always active and defaults to `http://localhost:4318/v1/metrics`, so without an endpoint it would log failed exports. `application.yaml` therefore sets `management.otlp.metrics.export.enabled: false`. Spring Boot maps the standard OpenTelemetry variables into a property source with the highest precedence, including `OTEL_METRICS_EXPORTER` to `management.otlp.metrics.export.enabled`, so a deployment sets `OTEL_EXPORTER_OTLP_ENDPOINT` and `OTEL_METRICS_EXPORTER=otlp`, both standard OpenTelemetry variables, and needs no application-specific switch.
*Alternatives:* switch metric export on from the presence of an endpoint with a custom `EnvironmentPostProcessor` and a lazily evaluated property source (one variable fewer, but Spring plumbing the standard variable makes unnecessary; chosen against by the user); always export and let it fail (noisy logs, violates clean output); a Spring profile (application-specific setting).

**D4. Sample all traces.** `management.tracing.sampling.probability: 1.0`. Volume is a few spans per file, and an incident on one file needs that file's trace.
*Alternatives:* the 10% default (most files would have no trace); tail sampling in Alloy (possible later, independent of the application).

**D5. One span per step attempt, linked by `sepa.file.id`, not one trace per file.** Steps run as separate work items, possibly on other instances and after restarts. Carrying a trace context through the work queue would need a new column and a migration and would create traces lasting minutes. Tempo finds all spans of a file with TraceQL `{ span.sepa.file.id = "<id>" }`, and logs carry the same `fileId`.
*Alternatives:* store `traceparent` in `work_item` and continue the trace in each step (migration, long-lived traces, little gain over search by attribute); span links (same storage need).

**D6. Micrometer Observation API in the processing module, created on the thread that does the work.** `StepWorker` already sets the MDC on the attempt's own virtual thread; the observation `sepa.processing.step` is started there too, with low-cardinality keys `step` and `outcome` and the high-cardinality key `sepa.file.id` (span attribute only, never a metric tag). The receive span is an observation `sepa.intake.receive` around the handling of one claimed file in the intake. One observation yields the span and the timer, so no separate `Timer` code.
*Alternatives:* the OpenTelemetry `Tracer` API directly (ties module code to OpenTelemetry and needs separate metric code); `@Observed` annotations (the step runs on a thread created inside `StepWorker`, outside any proxy).

**D7. Masking as an exporter decorator, reusing `LogMasking.mask`.** The console customizer only sees the JSON output; the OTLP path bypasses it. A `MaskingLogRecordExporter` and a `MaskingSpanExporter` wrap the exporters Spring Boot creates (via a `BeanPostProcessor` on `LogRecordExporter` and `SpanExporter` beans) and mask every string in bodies, attributes, span names, status descriptions and event attributes before data leaves the process. `LogMasking.mask` becomes the one masking function used by all three paths; masking moves to a shared class if `LogMasking` grows beyond the console customizer.
*Alternatives:* a `LogRecordProcessor` that mutates records (the SDK's `ReadWriteLogRecord` cannot replace the body in all SDK versions, and spans have no equivalent mutating hook after end); masking in the Alloy pipeline (data would leave the process unmasked, and the guarantee would depend on deployment configuration).

**D8. Logback configuration file with Spring Boot's console appender plus the OpenTelemetry appender.** `logback-spring.xml` includes Spring Boot's `defaults.xml` and `console-appender.xml` (which honour `logging.structured.format.console` and the masking customizer) and adds `OpenTelemetryAppender` with `captureMdcAttributes=fileId` and code and exception attributes. A component installs the appender with the `OpenTelemetry` bean once the context is ready, as the Spring Boot reference shows. Until then (startup lines) records are only on the console.
*Alternatives:* adding the appender programmatically to the root logger (hidden configuration, breaks on logging system re-initialisation).

**D9. Tests against `grafana/otel-lgtm`.** Testcontainers' `LgtmStackContainer` (module `testcontainers-grafana`) runs an OpenTelemetry collector, Loki, Tempo, Prometheus and Grafana in one container, matching the target stack; Spring Boot provides service connections for it, so `bootTestRun` sends to it and Grafana on the mapped port shows the local application. Tests query the Loki, Tempo and Prometheus HTTP APIs. Only the OTLP tests and `bootTestRun` start the container; the other integration tests keep running without an endpoint, which also exercises D3.
*Alternatives:* a plain OpenTelemetry collector with a file exporter (no proof that Grafana's backends accept and link the data); mock exporters (against project rules).

## Risks / Trade-offs

- [The Logback appender artifact is versioned with an `-alpha` suffix] → pinned and covered by dependency verification; its API (`OpenTelemetryAppender.install`) is the one the Spring Boot reference uses.
- [An unreachable collector makes the OpenTelemetry SDK log `Failed to export spans/logs` at ERROR (throttled), the OTLP meter registry at WARN] → accepted: a collector outage is an operational fault that should be visible; ERROR-based alerts fire on it, and processing is not affected. Silencing the SDK logger (outage invisible) and a custom state-change logger in the masking decorators (more code) were rejected by the user.
- [Exporter batching drops data when the collector is down for long] → acceptable for telemetry; the console ECS logs remain the complete record, and processing never waits on export.
- [Masking cost on every exported record] → one regex pass per string, as on the console today; volume is low.
- [Duplicate metrics if a deployment both scrapes and receives OTLP] → documented in the deployment change; collect one way.
- [The OTLP endpoint in a bank network may need TLS or auth headers] → Spring Boot supports SSL bundles and headers via properties and `OTEL_EXPORTER_OTLP_HEADERS`; fixed in the deployment change.

## Migration Plan

Deploy at any time; no data migration. Without `OTEL_EXPORTER_OTLP_ENDPOINT` the application behaves as before, except that console lines inside spans gain `traceId` and `spanId`. Rollback: revert the commit, or unset the variable to stop export.

## Open Questions

- The `gitops-workshop` Alloy needs OTLP pipelines for logs (to Loki's OTLP endpoint) and metrics (remote write or OTLP to Prometheus), and must stop tailing this application's pod console, or Loki holds every line twice. Decided in the deployment change, together with TLS and auth headers. Until then, traces arrive and logs and metrics are only available via pod console and scrape.
