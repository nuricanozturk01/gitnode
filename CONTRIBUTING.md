# Contributing to OriginHub

How to run and develop OriginHub locally.

## Prerequisites

Java **25** · Node **24** · pnpm **10** · Docker **20+** · Go **1.24+** (runner only)

```bash
corepack enable && corepack prepare pnpm@10 --activate
```

Maven bundled — use `./mvnw`.

---

## How it works

OriginHub is split into optional layers. Start only what you need.

```
┌─────────────────────────────────────────────────────────┐
│  Frontend (:4200)          Admin panel (:4300)          │  ← Angular dev servers (optional admin)
├─────────────────────────────────────────────────────────┤
│  Backend (:8080, SSH :2222)                             │  ← Spring Boot API + Git
├─────────────────────────────────────────────────────────┤
│  Postgres + Redis                                       │  ← required infra
├─────────────────────────────────────────────────────────┤
│  Prometheus (:9090) + Grafana (:3000)                   │  ← optional monitoring
└─────────────────────────────────────────────────────────┘
```

| Layer                | Port                    | Required?                                                    |
|----------------------|-------------------------|--------------------------------------------------------------|
| Postgres + Redis     | 5432, 6379              | Yes                                                          |
| Backend              | 8080, 2222              | Yes                                                          |
| Frontend             | 4200                    | Dev only (prod: embedded in backend image)                   |
| Admin API            | `/api/admin/**` on 8080 | On by default — disable with `ORIGINHUB_ADMIN_ENABLED=false` |
| Admin panel UI       | 4300                    | Optional — needs admin API                                   |
| Prometheus + Grafana | 9090, 3000              | Optional                                                     |

---

## Run profiles

### Base app — Frontend + Backend

Minimum for daily dev. No Grafana, no admin panel.

```bash
make dev-setup                    # once: infra + deps + local config
make dev-backend                  # terminal 1 → :8080
cd originhub-frontend && pnpm start   # terminal 2 → :4200
```

Docker (production image, no frontend dev server):

```bash
make up                           # Postgres + Redis + app container → :8080
```

### Full app — + Grafana + Admin

Everything enabled.

**Local dev:**

```bash
make dev-setup
make dev-backend                  # terminal 1 → :8080 (admin API on by default)
make monitoring                   # terminal 2 — Prometheus + Grafana
cd originhub-frontend && pnpm start       # terminal 3 → :4200
cd originhub-admin-panel && pnpm start    # terminal 4 → :4300
```

**Docker:**

```bash
make up                           # admin API on by default
make monitoring
cd originhub-admin-panel && pnpm start    # admin UI — separate dev server
```

**Disable admin API (Docker):** `ADMIN_ENABLED=false make up` on first start, or
`make app-stop && ADMIN_ENABLED=false make app` if the container already exists.

Local dev admin login (from `application-local.yaml.example`): `admin` / `Admin123`

---

## Makefile reference

### Setup & dev

| Command             | Runs                                                                           |
|---------------------|--------------------------------------------------------------------------------|
| `make dev-setup`    | Local config template + Postgres/Redis + `pnpm install` (frontend, admin, e2e) |
| `make dev-backend`  | Backend with `local` profile → http://localhost:8080                           |
| `make test`         | Backend unit tests + runner tests + frontend/admin/e2e lint                    |
| `make test-backend` | `./mvnw test`                                                                  |
| `make test-runner`  | Go tests in `originhub-runner/`                                                |
| `make test-lint`    | ESLint on all JS/TS workspaces                                                 |
| `make verify`       | Full backend CI gate (checkstyle, fmt, spotbugs)                               |

### Docker — core stack

| Command           | Runs                                                |
|-------------------|-----------------------------------------------------|
| `make up`         | Postgres + Redis + app container (:8080, SSH :2222) |
| `make down`       | Stop and remove app + infra containers              |
| `make infra`      | Postgres + Redis only                               |
| `make infra-down` | Stop and remove infra containers                    |
| `make app`        | App container only (infra must be up)               |
| `make app-stop`   | Stop and remove app container                       |
| `make ps`         | List running OriginHub containers                   |
| `make logs`       | Follow app container logs                           |
| `make logs-db`    | Follow Postgres logs                                |
| `make logs-redis` | Follow Redis logs                                   |
| `make purge`      | Stop all + delete volumes ⚠️                        |

### Docker — optional

| Command                                         | Runs                                                                               |
|-------------------------------------------------|------------------------------------------------------------------------------------|
| `make monitoring`                               | Prometheus (:9090) + Grafana (:3000)                                               |
| `make monitoring-down`                          | Stop Prometheus + Grafana                                                          |
| `make logs-prometheus`                          | Follow Prometheus logs                                                             |
| `make logs-grafana`                             | Follow Grafana logs                                                                |
| `ADMIN_ENABLED=false make up`                   | Stack without admin API (first start)                                              |
| `make app-stop && ADMIN_ENABLED=false make app` | Recreate app without admin API                                                     |
| `make ldap-up`                                  | OpenLDAP test container (LDAP E2E)                                                 |
| `make ldap-down`                                | Stop OpenLDAP container                                                            |
| `make saml-keygen`                              | SAML signing keys → `~/.originhub/saml/`                                           |
| `make actions-encryption-key`                   | Actions workflow secrets vault AES-256 key → `~/.originhub/actions-encryption-key` |

### Runner

| Command                 | Runs                                        |
|-------------------------|---------------------------------------------|
| `make runner-build`     | Go runner binary → `originhub-runner/dist/` |
| `make runner-build-all` | Runner binaries for all platforms           |

Run `make help` for the full list.

---

## URLs

| Service           | URL                                   |
|-------------------|---------------------------------------|
| App / API         | http://localhost:8080                 |
| Frontend (dev)    | http://localhost:4200                 |
| Admin panel (dev) | http://localhost:4300                 |
| SSH Git           | localhost:2222                        |
| Prometheus        | http://localhost:9090                 |
| Grafana           | http://localhost:3000 (admin / admin) |

---

## Local config

```bash
cp originhub-backend/src/main/resources/application-local.yaml.example \
   originhub-backend/src/main/resources/application-local.yaml
```

`make dev-setup` does this automatically. File is gitignored.

Admin API is **on by default** (`originhub.admin.enabled: true` in the example). To disable locally:

```yaml
originhub:
  admin:
    enabled: false
```

---

## Tests

```bash
make test                         # no server needed

# E2E — backend must be running
make dev-backend
cd e2e && pnpm test:e2e
```

See [e2e/README.md](e2e/README.md) for API-only and scenario commands.

---

## Module READMEs

| Path                                                               | What                    |
|--------------------------------------------------------------------|-------------------------|
| [originhub-backend/README.md](originhub-backend/README.md)         | Backend modules, Flyway |
| [originhub-frontend/README.md](originhub-frontend/README.md)       | Angular SPA scripts     |
| [originhub-admin-panel/README.md](originhub-admin-panel/README.md) | Admin UI                |
| [originhub-runner/README.md](originhub-runner/README.md)           | CI/CD runner agent      |
| [monitoring/README.md](monitoring/README.md)                       | Prometheus + Grafana    |
| [e2e/README.md](e2e/README.md)                                     | Playwright tests        |

---

## Repo layout

```
originhub/
├── originhub-backend/      API + Git (Spring Boot)
├── originhub-frontend/     Main UI (Angular, :4200)
├── originhub-admin-panel/  Platform admin UI (Angular, :4300)
├── originhub-runner/       CI/CD agent (Go)
├── originhub-events/       Shared domain events
├── e2e/                    Playwright tests
├── monitoring/             Prometheus + Grafana config
├── docker-compose.yml      Postgres + Redis (+ monitoring profile)
└── Makefile                All commands above
```
