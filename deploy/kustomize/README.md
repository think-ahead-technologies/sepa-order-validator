# Kubernetes deployment (Kustomize)

`base/` holds what every environment shares. `overlays/example/` is a template for one environment: copy it to
`overlays/<env>/` (dev, staging, prod) and replace every value. Ingress is not part of the manifests: the app takes no
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
