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
| Grafana UI | http://grafana.originhub.local | https://{domain.grafanaHost} |
| Prometheus | in-cluster `originhub-prometheus:9090` | same (internal) |

Domain: set **`domain.grafanaHost`** in `local.yml` / `prod.yml`.

Production Grafana password — base64 in `monitoring.secrets.GF_SECURITY_ADMIN_PASSWORD` (`prod.yml`).

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
