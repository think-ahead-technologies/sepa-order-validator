# Kubernetes deployment (Kustomize)

`base/` holds what every environment shares. `overlays/example/` is a template for one environment: copy it to
`overlays/<env>/` (dev, staging, prod) and replace every value. `overlays/local/` runs the app on the local OrbStack
cluster of the `gitops-workshop` repository (see below). Ingress is not part of the manifests: the app takes no
inbound business traffic.

```sh
kubectl kustomize deploy/kustomize/overlays/<env>            # render
kubectl apply -k deploy/kustomize/overlays/<env>             # apply (production only through the pipeline)
```

## What runs

- `Deployment` with 2 replicas. One pod is elected intake leader through PostgreSQL locks; all pods work the queue
  (baseline design A5, A6). Rolling updates keep every old pod until a new one is ready.
- Hardened pod for the OpenShift `restricted-v2` SCC: non-root with a UID assigned by the platform, read-only root
  file system with an `emptyDir` on `/tmp`, no capabilities, `RuntimeDefault` seccomp, no ServiceAccount token.
- Probes on `/actuator/health/{liveness,readiness}`; a startup probe allows up to 3 minutes for Flyway migrations.
- `Service` on port 8080 for Prometheus to scrape `/actuator/prometheus`.
- `PodDisruptionBudget` keeping at least one pod during node drains.

## What the environment provides

The overlay generates two ConfigMaps:

| ConfigMap | Content |
|-----------|---------|
| `payment-order-validator-env` | `SPRING_DATASOURCE_URL`, `SEPA_SFTP_HOST`, `SEPA_SFTP_PORT`, `SEPA_SFTP_USERNAME`, and any other override of `application.yaml` as environment variable |
| `payment-order-validator-sftp-known-hosts` | key `known_hosts`: host keys of the customer SFTP server (`sepa.sftp.known-hosts` is mandatory) |

Two Secrets must exist in the namespace before the pods start. They come from the central secrets manager (POL-8);
never commit them or generate them from literals in an overlay.

| Secret | Keys |
|--------|------|
| `payment-order-validator-db` | `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| `payment-order-validator-sftp` | `private-key` (OpenSSH or PEM), optional `passphrase` |

The app authenticates to SFTP with the private key. Password authentication is not wired in the manifests, because the
app accepts exactly one of password and private key.

PostgreSQL is not part of the manifests: the environment provides the database. Instance clocks must be
NTP-synchronised (baseline design A5).

## Image

The `Dockerfile` at the repository root builds the jar with the JDK pinned in `mise.toml` and runs it on the matching
JRE as UID 1001 in group 0, so OpenShift can assign any UID. Tests are not run in the image build; CI runs them.

```sh
docker build -t payment-order-validator:<tag> .
```

## Local cluster (`overlays/local/`)

Flux in the `gitops-workshop` repository reconciles this overlay into the namespace `sepa`. That repository provides
PostgreSQL, a customer SFTP server and the Grafana stack, and its `apps/sepa/prepare-local.sh` builds the image
`payment-order-validator:0.1.0-local` into OrbStack's shared image store and creates the Secrets and the known_hosts
ConfigMap listed above. The overlay adds:

- a `ServiceMonitor`, so the kube-prometheus-stack Prometheus scrapes `/actuator/prometheus`;
- `OTEL_EXPORTER_OTLP_ENDPOINT` pointing at Grafana Alloy (`alloy.monitoring.svc:4318`); it takes effect once the
  application exports OTLP;
- 1 GiB memory per pod instead of 2 GiB.
