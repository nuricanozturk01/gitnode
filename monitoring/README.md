# Monitoring

Prometheus + Grafana.

## Docker (local dev)

**Optional** — not started by default.

| Command | Runs |
|---------|------|
| `make monitoring` | Prometheus :9090 + Grafana :3000 |
| `make monitoring-down` | Stop both |

Grafana login: `admin` / `admin`

## How it works

- Prometheus scrapes `GET /actuator/prometheus` on the backend
- Grafana datasource + **GitNode dashboard** provisioned from `monitoring/grafana/provisioning/`

Config sources:

| Path | Used by |
|------|---------|
| `monitoring/prometheus.yml` | Docker Compose |
| `monitoring/grafana/provisioning/` | Docker Compose |

App metrics export: `GITNODE_OBSERVABILITY_ENABLED=true`
