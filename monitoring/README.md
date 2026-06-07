# Monitoring

Prometheus + Grafana. **Optional** — not started by default.

Run profile: [CONTRIBUTING.md](../CONTRIBUTING.md#full-app--grafana--admin)

## Run

| Command | Runs |
|---------|------|
| `make monitoring` | Prometheus :9090 + Grafana :3000 |
| `make monitoring-down` | Stop both |

Grafana login: `admin` / `admin`

## How it works

- Prometheus scrapes `GET /actuator/prometheus` on the app
- Grafana dashboards pre-provisioned in `monitoring/grafana/provisioning/`
- App exports metrics even without these containers (`ORIGINHUB_OBSERVABILITY_ENABLED`)

Config: `monitoring/prometheus.yml`, `monitoring/grafana/provisioning/`
