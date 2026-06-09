<div align="center">

<br/>

<h1><img src="images/icon.png" width="50" valign="middle" /> &nbsp;<b>Git</b><span style="font-weight:400">Node</span></h1>

<h3>A simple, self-hosted Git registry — your code, your server, your rules.</h3>

<br/>

[![Release](https://img.shields.io/badge/release-v2.0.3-blue?style=for-the-badge)](https://github.com/nuricanozturk01/gitnode/releases/tag/v2.0.3)
[![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge&logo=openjdk)](https://openjdk.org/)
[![Go](https://img.shields.io/badge/Go-1.24-00ADD8?style=for-the-badge&logo=go&logoColor=white)](https://go.dev/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-21-DD0031?style=for-the-badge&logo=angular)](https://angular.dev)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind-4.x-38BDF8?style=for-the-badge&logo=tailwindcss&logoColor=white)](https://tailwindcss.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-336791?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7.4-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)
[![Prometheus](https://img.shields.io/badge/Prometheus-ready-E6522C?style=for-the-badge&logo=prometheus&logoColor=white)](https://prometheus.io/)
[![Grafana](https://img.shields.io/badge/Grafana-ready-F46800?style=for-the-badge&logo=grafana&logoColor=white)](https://grafana.com/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)

<br/>

[✨ Features](#-features) · [🛠 Tech Stack](#-tech-stack) · [🚀 Getting Started](#-getting-started) · [🗺 Roadmap](#-roadmap) · [📄 License](#-license)

<br/>

</div>

---

## 🔍 What is GitNode?

GitNode is a simple, open-source, self-hosted Git registry inspired by GitHub. It gives you full control over your
repositories, pull requests, and CI/CD pipelines (YAML workflows + self-hosted runner agent) — running entirely on your
own infrastructure, with zero dependency on third-party platforms.

No subscriptions. No data leaving your servers. No vendor lock-in. Just Git, hosted your way.

GitNode is built for developers and teams who care about ownership — whether you're an indie developer running it on a
VPS, or an enterprise team deploying it on private infrastructure. If you've ever thought *"I wish GitHub ran on my own
server"*, GitNode is for you.

---

## ✨ Features

GitNode covers the full Git hosting loop — repos, review, browsing, issues, project boards, releases, webhooks, code
snippets, and collaborator access — plus **CI/CD Actions** (YAML workflows + Go runner agent), **enterprise SAML/LDAP
SSO**, **platform admin tooling**, **rate limiting**, **Prometheus/Grafana observability**, and **audit logging** — all
on your own infrastructure.

<div align="center">

|                                                     |                                               |                                                         |
|:---------------------------------------------------:|:---------------------------------------------:|:-------------------------------------------------------:|
| 📁 [Repository Management](#-repository-management) |    👤 [Public Profiles](#-public-profile)     |  📥 [GitHub Migration](#-github-repository-migration)   |
|         🗂 [Code Browsing](#-code-browsing)         |      🔀 [Pull Requests](#-pull-requests)      |                  🐛 [Issues](#-issues)                  |
|  📋 [Project Boards](#-project-management-kanban)   | 📝 [Code Snippets](#-code-snippets-gist-like) |         🏷 [Tags & Releases](#-tags--releases)          |
|              🔔 [Webhooks](#-webhooks)              |     🔐 [Authentication](#-authentication)     |           👥 [Collaborators](#-collaborators)           |
|      🍴 [Repository Forks](#-repository-forks)      | 🛡 [Access Policies](#-repo-access-policies)  |    🏢 [Enterprise SSO](#-enterprise-saml--ldap-sso)     |
|           📊 [Admin Panel](#-admin-panel)           |      ⚡ [Rate Limiting](#-rate-limiting)       | 📈 [Observability](#-prometheus--grafana-observability) |
|         📜 [Audit Logging](#-audit-logging)         |         ⚡ [Actions](#-actions--cicd)          |                                                         |

</div>

---

### 📁 Repository Management

- Create, clone, push, and pull repositories
- **Public and private** repositories, descriptions, and **topics**
- **Git over HTTP and HTTPS (TLS)**: smart HTTP backend at `/git/…` — use `http://` or `https://` remote URLs with your
  GitNode host
- **SSH** Git on a configurable port (default **2222** in Docker)
- Per-repo **Settings**: general metadata, optional **auto-delete head branch** after PR merge or close

### 👤 Public Profile

- Every account has a public profile at `/:username` showing public repositories
- Optional **profile README** rendered from the account's special repository
- Paginated public repository list

### 📥 GitHub Repository Migration

- **Migrate from GitHub** with a repository URL and **personal access token** (classic or fine-grained with repo read)
- **Mirror clone** the Git history into your GitNode account
- Optionally migrate **pull requests** from GitHub in the same job

### 🗂 Code Browsing

- File tree with breadcrumbs; blob viewer and **raw** file URLs
- **Markdown README** on the repo home (images and relative links resolved like on GitHub)
- Commit history and diffs

### 🔀 Pull Requests

- Open, review, merge, or close PRs
- Merge strategies: **merge commit**, **squash**, **rebase**
- Draft PRs, inline discussion, file-level comments

### 🐛 Issues

- Track bugs and feature requests per repository
- Labels, comments, open/close status
- **Link issues to Kanban tasks** — resolving a PR can auto-complete linked tasks

### 📋 Project Management (Kanban)

- **Projects** with **boards** and configurable **columns** (per-project)
- **Tasks** and **subtasks** with types, status, assignee, and ordering
- Create **Git branches** from a task or subtask (conventional branch names, e.g. `TASK-1` or `TASK-1.SUB-1-…`)
- **Link** a branch's pull request to the task or subtask; see PR status on the card
- **Optional automation** (per project): when a linked PR is **merged**, mark the task or subtask **completed**
- **Project settings** page for the above PR → status behaviour
- Projects linked to a repository are paginated in the repo's **Projects** tab

### 📝 Code Snippets (Gist-like)

- Create **public** or **private** snippets with syntax-highlighted code blocks
- **Multi-file** support per snippet
- Full **revision history** — track edits and diff between revisions
- **Fork** any public snippet
- Paginated snippets per repository in the repo's **Snippets** tab
- Manage all your snippets from the **Snippets** section in the app bar

### 🔔 Webhooks

- **Signed HTTP delivery** (`X-Hub-Signature-256`) to your services for pushes, PR events, and more
- **Automatic retries** (3 attempts, exponential back-off) on delivery failure
- **Dead-letter queue (DLQ)** — permanently failed deliveries are queued and retried on a schedule; admin can inspect
  and replay from the admin panel
- **Per-host circuit breaker** (Resilience4j) — when a target endpoint fails repeatedly the circuit opens; subsequent
  deliveries go straight to the DLQ instead of burning retries; circuit auto-recovers when the endpoint comes back
- Configured per-repository in **Settings → Webhooks**

### 🏷 Tags & Releases

- Create **lightweight and annotated tags** on any commit via the UI
- **Draft or publish releases** tied to a tag — write release notes with Markdown
- **Upload release assets** (binaries, archives, checksums) directly from the browser
- Browse all releases in the repo's **Releases** tab; latest release shown on the repo home
- **Delete** releases or tags from the UI (tag is removed from the underlying Git repo)
- **Release badge** on the repo home shows the latest published version at a glance

### 👥 Collaborators

- Invite other GitNode users to your repository with **fine-grained per-permission roles**
- Available permissions (each toggled independently): **Push**, **Pull Request management**, **Issue management**, *
  *Settings access**, **Admin** (all permissions)
- Share an **invite link** with a configurable expiry — recipient accepts via the link, no admin approval needed
- Manage active collaborators and revoke access at any time from **Settings → Collaborators**
- Collaborators inherit the base repo visibility — private repos remain private to non-collaborators
- **How to invite:** go to your repository → Settings → Collaborators → *Invite* → pick permissions → copy the generated
  link and send it to the person you want to add

### 🍴 Repository Forks

- Fork any public repository to your own account with a single click
- Fork preserves the full commit history of the upstream repo at the time of forking
- Work on your fork independently — push branches, open issues, create snippets
- Open a **pull request from your fork** back to the upstream repository to propose changes
- **How to fork:** navigate to any public repository → click **Fork** in the top-right area of the repo header

### 🛡 Repo Access Policies

- Define **access rules** per repository that apply on top of base visibility
- Policies control what authenticated (non-owner, non-collaborator) users can do — e.g. **allow public read but restrict
  push**, or **allow fork but restrict issue creation**
- Useful for organizations that want open-source-style read access without enabling arbitrary contributions
- Configured in **Settings → Access Policies**; changes take effect immediately for all subsequent requests

### ⚡ Actions — CI/CD

- **YAML workflow definitions** checked in at `.gitnode/workflows/*.yml` — `push`, `pull_request`, and
  `workflow_dispatch` triggers
- **Job graph** with matrix strategy expansion and `needs` dependency ordering; `concurrency` groups with cancellation
- **Runner protocol**: runners register via token, receive jobs over **WebSocket**, report step logs and status back to
  the server
- **Shell and Docker executors** — built-in `actions/checkout@v1` step; custom `run` steps execute in a per-job
  workspace
- **Secrets vault** — AES-256-GCM encrypted secrets per repo; injected as env vars at job runtime (masked in logs)
- **Artifact store** — upload/download artifacts by run + name; retained per run
- **Cache store** — key-based cache for workflow dependencies
- **SSE log streaming** — real-time step logs via Server-Sent Events (
  `GET /api/repos/{owner}/{repo}/actions/runs/{runId}/events`)
- **Run history** — list, cancel, and re-run workflows from the UI
- **Runner management** — register, list, delete runners per repo; runner groups per org
- **Admin panel Actions tab** — platform-wide runner stats, workflow run counts

#### Go runner (`gitnode-runner/`)

Standalone agent that connects to the server. Single static binary (~12 MB), no JRE needed.

```bash
cd gitnode-runner
make build         # dist/gitnode-runner (local arch)
make build-all     # Linux amd64/arm64, macOS arm64, Windows amd64

./dist/gitnode-runner start \
  --server-url http://gitnode.company.com \
  --token ghrt_xxxxxxxxxxxx \
  --name my-runner \
  --labels self-hosted,linux,docker \
  --executor docker \       # or: shell
  --work-dir /tmp/gitnode-runner \
  --concurrent-jobs 2
```

Config file (`~/.gitnode-runner/config.yml`) is written automatically after first registration — subsequent starts use
`runner_token` from that file.

### 🏢 Enterprise SAML & LDAP SSO

- **Per-organization identity** — map email domains to a SAML 2.0 IdP or corporate LDAP directory
- **SAML 2.0 service provider** — metadata URI, connection test, cached IdP XML, SP entity ID override
- **LDAP directory auth** — manager bind, user search base/filter, email and display-name attributes, optional group
  mapping
- **Work-email login flow** — users enter work email on the login page; GitNode routes to the correct org and
  provisions accounts on first successful sign-in
- **Mutually exclusive per org** — SAML and LDAP cannot both be enabled on the same organization
- Configure in the **admin panel** (`gitnode-admin-panel`, port **4300** in local dev)

### 📊 Admin Panel

- Dev: `cd gitnode-admin-panel && pnpm start` → http://localhost:4300
- See [gitnode-admin-panel/README.md](gitnode-admin-panel/README.md)

Features when enabled:

- **Dashboard** — users, repositories, organizations, storage; activity tables (daily/weekly); top contributors; cached
  stats
- **Users** — search, enable/disable accounts
- **Organizations** — create, edit, delete; configure **SAML** or **LDAP** per org; test connections before enabling
- **Audit log API** — query application audit events (`GET /api/admin/audit-logs`)

See [`gitnode-admin-panel/README.md`](gitnode-admin-panel/README.md) for setup. Platform admin access needs
bootstrap credentials (see Environment Variables in README).

### ⚡ Rate Limiting

- Redis-backed sliding-window limits on sensitive endpoints
- Covers authentication (login, register, password recovery), repo/PR/issue creation, webhooks, tags, snippets, and
  SSO/LDAP discovery
- Returns **429** with `rateLimitExceeded` when limits are hit

### 📈 Prometheus & Grafana Observability

**Optional.** Monitoring containers are not started by default — run `make monitoring` when needed.

- **Micrometer** metrics exported at `/actuator/prometheus` (toggle with `GITNODE_OBSERVABILITY_ENABLED`)
- **Docker Compose profile `monitoring`** — Prometheus (**9090**) and Grafana (**3000**, admin / admin)
- See [monitoring/README.md](monitoring/README.md) for setup
- Scrape targets: app container (`gitnode:8080`) or host-run backend (`host.docker.internal:8080`)
- **Circuit breaker health** at `/actuator/circuitbreakers` — real-time `CLOSED / OPEN / HALF_OPEN` state for webhook
  delivery and SAML metadata circuit breakers; included in `/actuator/health` details

### 📜 Audit Logging

- **Application audit log** — `@Audited` actions persisted to partitioned `audit_logs` tables (append-only triggers)
- **Admin API** — paginated queries by actor and recent window
- **pgAudit** — Postgres image logs write, DDL, and role operations (`shared_preload_libraries=pgaudit`). Admin log
  viewer is **off by default** — set `GITNODE_ADMIN_PGAUDIT_ENABLED=true` and mount the Postgres log volume into the
  app container.
- Toggle application audit with `GITNODE_AUDIT_ENABLED` (default `true`)

### 🔐 Authentication

- Bearer Auth username + password with JWT
- Basic Auth for git repo operations
- OAuth2: **Google**, **GitHub**, **GitLab**
- SSH public keys for Git over SSH
- **Enterprise SAML 2.0** and **LDAP** per organization (see above)

---

## 🛠 Tech Stack

| Layer         | Technology                                                                                                                     |
|---------------|--------------------------------------------------------------------------------------------------------------------------------|
| Language      | Java 25                                                                                                                        |
| Framework     | Spring Boot 4, Spring Security, Spring Data JPA                                                                                |
| Git Engine    | Eclipse JGit                                                                                                                   |
| SSH Server    | Apache MINA SSHD                                                                                                               |
| Auth          | JWT, OAuth2 (Google · GitHub · GitLab), SAML 2.0, LDAP                                                                         |
| Database      | PostgreSQL 17 + Flyway, pgAudit                                                                                                |
| Cache         | Redis (cache + rate limiting)                                                                                                  |
| Observability | Micrometer, Prometheus, Grafana                                                                                                |
| Resilience    | Resilience4j circuit breakers (webhook delivery, SAML metadata)                                                                |
| Audit         | Application audit log (partitioned PostgreSQL)                                                                                 |
| CI/CD Engine  | Spring Boot `actions` module — WebSocket runner protocol, SSE log streaming, secrets vault (AES-256-GCM), artifact/cache store |
| Runner Agent  | Go 1.24 (`gitnode-runner`) — shell + Docker executors, single static binary                                                    |
| Frontend      | Angular 21, TypeScript 5                                                                                                       |
| Admin UI      | Angular 21 (`gitnode-admin-panel`)                                                                                             |
| Styling       | Tailwind CSS 4, DaisyUI 5                                                                                                      |
| Container     | Docker (multi-stage build, single image)                                                                                       |

---

## 🚀 Getting Started

> 📖 Documentation is available in-app at **`/docs`** once GitNode is running.

### Developing locally

See **[CONTRIBUTING.md](CONTRIBUTING.md)** for run profiles, Makefile commands, and module docs.

**Base app** (Frontend + Backend):

```bash
make dev-setup && make dev-backend          # terminal 1
cd gitnode-frontend && pnpm start         # terminal 2 → :4200
```

**Full app** (+ Grafana + Admin): see [CONTRIBUTING.md#run-profiles](CONTRIBUTING.md#run-profiles)

```bash
make test    # unit tests + lint
```

### Option 1 — Docker Run

```bash
SECRET=$(openssl rand -base64 64 | tr -d '\n')

# Infrastructure (Postgres with pgAudit, Redis) — creates the gitnode network
docker compose up -d

# Optional: Prometheus + Grafana
docker compose --profile monitoring up -d

# Optional: admin panel UI (dev, separate terminal)
# cd gitnode-admin-panel && pnpm install && pnpm start   → http://localhost:4300

docker run -d \
  --name gitnode \
  --network gitnode \
  -p 8080:8080 \
  -p 2222:2222 \
  -e SPRING_PROFILES_ACTIVE=os \
  -e "GITNODE_JWT_SECRET=$SECRET" \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://gitnode-postgres:5432/gitnode \
  -e SPRING_DATASOURCE_USERNAME=admin \
  -e SPRING_DATASOURCE_PASSWORD=admin123 \
  -e SPRING_DATA_REDIS_HOST=gitnode-redis \
  -e SPRING_DATA_REDIS_PORT=6379 \
  -e GITNODE_GIT_REPO__ROOT=/data/repos \
  -e GITNODE_BOOTSTRAP_ADMIN_ENABLED=true \
  -e GITNODE_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e GITNODE_BOOTSTRAP_ADMIN_PASSWORD=Admin123 \
  -e GITNODE_ADMIN_MODULITH_EVENTS_ENABLED=true \
  -e GITNODE_AUDIT_ENABLED=true \
  -e GITNODE_OBSERVABILITY_ENABLED=false \
  -e GITNODE_CORS_ALLOWED_ORIGINS=http://localhost:4200,http://localhost:4300 \
  -v gitnode-repos:/data/repos \
  repo.repsy.io/nuricanozturk/gitnode/gitnode-os:latest
```

### Option 2 — Makefile (recommended)

**Base stack** — Postgres + Redis + app:

```bash
make up          # → http://localhost:8080
```

**Optional add-ons:**

```bash
make monitoring                  # Prometheus + Grafana
cd gitnode-admin-panel && pnpm start   # admin UI → :4300 (API on by default)
```

All commands: **[CONTRIBUTING.md](CONTRIBUTING.md#makefile-reference)**

| Service        | URL                   | Default                      |
|----------------|-----------------------|------------------------------|
| App            | http://localhost:8080 | ✅                            |
| SSH Git        | localhost:2222        | ✅                            |
| Frontend (dev) | http://localhost:4200 | manual — `pnpm start`        |
| Admin panel    | http://localhost:4300 | manual — `pnpm start`        |
| Prometheus     | http://localhost:9090 | optional — `make monitoring` |
| Grafana        | http://localhost:3000 | optional — `make monitoring` |

### Environment Variables

| Variable                                | Required | Default                                       | Description                                       |
|-----------------------------------------|----------|-----------------------------------------------|---------------------------------------------------|
| `GITNODE_ADMIN_PGAUDIT_ENABLED`         |          | `false`                                       | Admin panel pgAudit log viewer                    |
| `GITNODE_ADMIN_PGAUDIT_LOG_DIRECTORY`   |          | —                                             | Postgres log dir in app container (enable viewer) |
| `GITNODE_JWT_SECRET`                    | ✅        | —                                             | Min 32-char secret for JWT signing                |
| `GITNODE_BOOTSTRAP_ADMIN_USERNAME`      |          | `admin`                                       | First-start platform admin username               |
| `GITNODE_BOOTSTRAP_ADMIN_PASSWORD`      | ✅ prod   | —                                             | Bootstrap admin password (empty skips)            |
| `GITNODE_PLATFORM_ADMIN_USERNAMES`      |          | —                                             | Comma-separated platform admin usernames          |
| `GITNODE_GIT_REPO__ROOT`                |          | `/data/repos`                                 | Git repository storage path                       |
| `GITNODE_FRONTEND_BASE_URL`             |          | `http://localhost:8080`                       | Public base URL                                   |
| `GITNODE_CORS_ALLOWED_ORIGINS`          |          | `http://localhost:4200,http://localhost:4300` | CORS origins (add admin panel URL)                |
| `GITNODE_AUDIT_ENABLED`                 |          | `true`                                        | Application audit log                             |
| `GITNODE_OBSERVABILITY_ENABLED`         |          | `true`                                        | Prometheus `/actuator/prometheus`                 |
| `GITNODE_SSO_SAML_ENABLED`              |          | `false`                                       | Global SAML feature flag                          |
| `GITNODE_SSO_LDAP_ENABLED`              |          | `false`                                       | Global LDAP feature flag                          |
| `GITNODE_SSO_SAML_SP_SIGNING_KEY_PATH`  |          | —                                             | SP signing private key (SAML)                     |
| `GITNODE_SSO_SAML_SP_SIGNING_CERT_PATH` |          | —                                             | SP signing certificate (SAML)                     |
| `SPRING_DATA_REDIS_HOST`                |          | `gitnode-redis`                               | Redis hostname                                    |
| `SPRING_DATA_REDIS_PORT`                |          | `6379`                                        | Redis port                                        |
| `OAUTH2_GOOGLE_CLIENT_ID`               |          | —                                             | Google OAuth2 client ID                           |
| `OAUTH2_GOOGLE_CLIENT_SECRET`           |          | —                                             | Google OAuth2 client secret                       |
| `OAUTH2_GITHUB_CLIENT_ID`               |          | —                                             | GitHub OAuth2 client ID                           |
| `OAUTH2_GITHUB_CLIENT_SECRET`           |          | —                                             | GitHub OAuth2 client secret                       |
| `OAUTH2_GITLAB_CLIENT_ID`               |          | —                                             | GitLab OAuth2 client ID                           |
| `OAUTH2_GITLAB_CLIENT_SECRET`           |          | —                                             | GitLab OAuth2 client secret                       |

---

## 📄 License

Distributed under the [MIT License](LICENSE.txt).

---

## ☕ Support

<div align="center">

If GitNode saves you time or you just want to say thanks, consider buying me a coffee. It keeps the project alive and
the commits coming.

<a href="https://www.buymeacoffee.com/nuricanozturk" target="_blank">
  <img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" height="50" />
</a>

</div>
