# Helm chart: payment-order-validator

Deploys the SEPA Payment Order Validator. Ingress is not part of the chart: the app takes no inbound business
traffic.

```sh
helm lint deploy/helm/payment-order-validator -f <env-values>.yaml --strict
helm template sepa deploy/helm/payment-order-validator -n <namespace> -f <env-values>.yaml     # render
helm upgrade --install sepa deploy/helm/payment-order-validator -n <namespace> -f <env-values>.yaml
```

Production deploys run only through the pipeline. `ci/example-values.yaml` is a template for one environment's values.

## What runs

- `Deployment` with 2 replicas. One pod is elected intake leader through PostgreSQL locks; all pods work the queue
  (baseline design A5, A6). Rolling updates keep every old pod until a new one is ready.
- Hardened pod for the OpenShift `restricted-v2` SCC: non-root with a UID assigned by the platform, read-only root
  file system with an `emptyDir` on `/tmp`, no capabilities, `RuntimeDefault` seccomp, no ServiceAccount token.
- Probes on `/actuator/health/{liveness,readiness}`; a startup probe allows up to 3 minutes for Flyway migrations.
- `Service` on port 8080 for Prometheus to scrape `/actuator/prometheus`.
- `PodDisruptionBudget` keeping at least one pod during node drains.
- Pods roll when the ConfigMaps change (checksum annotation).

## Required values

Rendering fails with a message when one is missing.

| Value | Content |
|-------|---------|
| `image.repository`, `image.digest` | the signed image; only references by digest are supported |
| `database.url` | JDBC URL of the PostgreSQL database |
| `sftp.host`, `sftp.username` | customer SFTP server and account (`sftp.port` defaults to 22) |
| `sftp.knownHosts` | `known_hosts` lines of the SFTP server; host keys are always verified |

`env` takes further environment variables, each overriding the matching property in `application.yaml`.

## Secrets

The chart creates no Secret. Two Secrets must exist in the namespace before the pods start. They come from the
central secrets manager (POL-8); never put them in values files.

| Secret (value holding its name) | Keys |
|---------------------------------|------|
| `payment-order-validator-db` (`database.existingSecret`) | `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| `payment-order-validator-sftp` (`sftp.existingSecret`) | `private-key` (OpenSSH or PEM), optional `passphrase` |

The app authenticates to SFTP with the private key. Password authentication is not wired in the chart, because the
app accepts exactly one of password and private key.

PostgreSQL is not part of the chart: the environment provides the database. Instance clocks must be NTP-synchronised
(baseline design A5).
