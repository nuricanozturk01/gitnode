# Monitoring

Prometheus + Grafana.

## Docker (local dev)

**Optional** — not started by default.

| Command | Runs |
|---------|------|
| `make monitoring` | Prometheus :9090 + Grafana :3000 |
| `make monitoring-down` | Stop both |

Grafana login: `admin` / `admin`

## Kubernetes (Helm)

Included when `monitoring.enabled: true` (default in `deploy/helm/originhub/values.yml`).

| Service | Local (kind) | Production |
|---------|--------------|------------|
| Grafana UI | http://grafana.originhub.test | https://{domain.grafanaHost} |
| Prometheus | in-cluster `originhub-prometheus:9090` | same (internal) |

Domain: set **`domain.grafanaHost`** in `deploy/helm/originhub/values.yml`.

Disable locally: `K8S_OBSERVABILITY=0 make k8s-bootstrap` or `monitoring.enabled: false` in values.

## How it works

- Prometheus scrapes `GET /actuator/prometheus` on the backend Service
- Grafana datasource + **OriginHub dashboard** provisioned from `monitoring/grafana/provisioning/`
- K8s chart bundles the same dashboard JSON under `deploy/helm/originhub/config/grafana/` (keep in sync when editing `monitoring/grafana/provisioning/dashboards/originhub.json`)

Config sources:

| Path | Used by |
|------|---------|
| `monitoring/prometheus.yml` | Docker Compose |
| `monitoring/grafana/provisioning/` | Docker Compose |
| `deploy/helm/originhub/templates/monitoring/` | Kubernetes Helm |

App metrics export: `ORIGINHUB_OBSERVABILITY_ENABLED=true` (set by Helm chart).
