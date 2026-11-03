<div align="center">

<h1><img src="images/icon.png" width="44" valign="middle" /> &nbsp;<b>Git</b>Node</h1>

<p><b>A self-hosted Git platform — repositories, code review, CI/CD and project management on your own infrastructure.</b></p>

[![Release](https://img.shields.io/badge/release-v2.0.3-blue?style=flat-square)](https://github.com/nuricanozturk01/gitnode/releases/tag/v2.0.3)
[![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-21-DD0031?style=flat-square&logo=angular)](https://angular.dev)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-336791?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE.txt)
<a href="https://status.gitnode.dev" target="_blank" rel="noopener noreferrer"><img src="https://api.cronbeat.dev/badge/A1HG1-tT7Jvw49lO_2d8tmVU/uptime.svg?range=90d" alt="GitNode uptime"></a>

[Overview](#overview) · [Features](#features) · [Quick start](#quick-start) · [Configuration](#configuration) · [Architecture](#architecture) · [Contributing](#contributing)

</div>

---

## Overview

GitNode is an open-source, GitHub-style Git platform that runs entirely on infrastructure you control: no subscription,
no data leaving your servers, no vendor lock-in. It suits an individual developer on a small VPS as well as a team
deploying on private infrastructure.

A single backend (Spring Boot modular monolith), an Angular web app, an optional admin panel and an optional Go runner
agent for CI/CD. In-app documentation is served at **`/docs`**.

## Features

| Area | Highlights |
|---|---|
| **Repositories** | Public/private repos, topics, forks, archive-style settings, branch protection, tags and releases with assets; Git over smart HTTP(S) and SSH |
| **Code review** | Pull requests (merge / squash / rebase), drafts, reviewers, approvals and change requests, inline and file comments, conflict resolution, required checks |
| **Planning** | Issues with labels, milestones and assignees; Kanban projects with boards, tasks, subtasks and branch / PR linking |
| **CI/CD (Actions)** | YAML workflows in `.gitnode/workflows`, matrix jobs, secrets vault (AES-256-GCM), artifacts and cache, live logs, self-hosted Go runner (shell and Docker executors) |
| **Collaboration** | Collaborator roles with fine-grained permissions and invite links, code snippets with revisions, signed webhooks with retries, dead-letter queue and circuit breaker, in-app notifications |
| **Identity & security** | JWT, OAuth2 (Google, GitHub, GitLab), SAML 2.0 and LDAP per organization, SSH keys, two-factor authentication (TOTP) with recovery codes, rate limiting, audit log |
| **AI (opt-in, bring your own key)** | PR code review, commit-message and PR-description suggestions, codebase analysis; OpenAI, Anthropic, Gemini, Ollama |
| **Operations** | Admin panel, GitHub migration, Prometheus / Grafana metrics, health and circuit-breaker endpoints, multi-instance ready |

### Go runner

A single static binary, no JRE required.

```bash
cd gitnode-runner && make build
./dist/gitnode-runner start --server-url http://gitnode.example.com --token ghrt_xxxx \
  --name my-runner --labels self-hosted,linux,docker --executor docker
```

## Quick start

**Requirements:** Docker, and `make` for the shortcuts below.

```bash
make up                        # Postgres + Redis + GitNode → http://localhost:8080
make monitoring                # optional: Prometheus (9090) and Grafana (3000)
```

| Service | URL |
|---|---|
| Web app and API | http://localhost:8080 |
| Git over SSH | `localhost:2222` |
| Admin panel (dev) | http://localhost:4300 — `cd gitnode-admin-panel && pnpm start` |

<details>
<summary><b>Plain <code>docker run</code> instead of Make</b></summary>

```bash
SECRET=$(openssl rand -base64 64 | tr -d '\n')
ACTIONS_KEY=$(openssl rand -base64 32 | tr -d '\n')
AI_KEY=$(openssl rand -base64 32 | tr -d '\n')

docker network create gitnode
docker run -d --name gitnode-postgres --network gitnode \
  -e POSTGRES_DB=gitnode -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=change-me \
  postgres:17-alpine
docker run -d --name gitnode-redis --network gitnode \
  redis:7-alpine redis-server --save "" --maxmemory 512mb --maxmemory-policy volatile-lru
docker run -d --name gitnode --network gitnode -p 8080:8080 -p 2222:2222 \
  -e SPRING_PROFILES_ACTIVE=os \
  -e "GITNODE_JWT_SECRET=$SECRET" \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://gitnode-postgres:5432/gitnode \
  -e SPRING_DATASOURCE_USERNAME=admin -e SPRING_DATASOURCE_PASSWORD=change-me \
  -e SPRING_DATA_REDIS_HOST=gitnode-redis \
  -e GITNODE_GIT_REPO__ROOT=/data/repos \
  -e GITNODE_BOOTSTRAP_ADMIN_PASSWORD='choose-a-strong-password' \
  -e "ACTIONS_ENCRYPTION_KEY=$ACTIONS_KEY" -e "GITNODE_AI_ENCRYPTION_KEY=$AI_KEY" \
  -e GITNODE_FRONTEND_BASE_URL=http://localhost:8080 \
  -e GITNODE_CORS_ALLOWED_ORIGINS=http://localhost:8080 \
  -v gitnode-repos:/data/repos \
  repo.repsy.io/nuricanozturk/gitnode/gitnode-os:latest
```

</details>

Development setup, run profiles and all Make targets are in **[CONTRIBUTING.md](CONTRIBUTING.md)**.

## Configuration

Everything is configured through environment variables. The ones you will almost always set:

| Variable | Description |
|---|---|
| `GITNODE_JWT_SECRET` | **Required.** Secret for signing JWTs (at least 32 characters) |
| `GITNODE_BOOTSTRAP_ADMIN_PASSWORD` | Password of the first platform admin (required in production) |
| `GITNODE_GIT_REPO__ROOT` | Storage path for Git repositories — mount a volume |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | PostgreSQL connection |
| `SPRING_DATA_REDIS_HOST` / `_PORT` | Redis connection (cache, rate limiting) |
| `ACTIONS_ENCRYPTION_KEY` | Base64 AES-256 key for the Actions secrets vault (`make actions-encryption-key`) |
| `GITNODE_AI_ENCRYPTION_KEY` | Base64 AES-256 key for stored AI API keys (`make ai-encryption-key`) |
| `GITNODE_FRONTEND_BASE_URL`, `GITNODE_CORS_ALLOWED_ORIGINS` | Public URLs used for OAuth/SAML redirects and CORS |

<details>
<summary><b>Full variable reference</b></summary>

| Variable                                | Required | Default                                       | Description                                                    |
|-----------------------------------------|----------|-----------------------------------------------|----------------------------------------------------------------|
| `GITNODE_JWT_SECRET`                    | ✅        | —                                             | Min 32-char secret for JWT signing                             |
| `GITNODE_BOOTSTRAP_ADMIN_PASSWORD`      | ✅ prod   | —                                             | Bootstrap admin password (empty skips bootstrap)               |
| `GITNODE_BOOTSTRAP_ADMIN_USERNAME`      |          | `admin`                                       | First-start platform admin username                            |
| `GITNODE_BOOTSTRAP_ADMIN_ENABLED`       |          | `true`                                        | Run bootstrap admin creation on startup                        |
| `GITNODE_PLATFORM_ADMIN_USERNAMES`      |          | —                                             | Comma-separated additional platform admin usernames            |
| `GITNODE_GIT_REPO__ROOT`                |          | `~/.gitnode`                                  | Git repository storage path (use a volume in Docker)           |
| `GITNODE_FRONTEND_BASE_URL`             |          | `http://localhost:4200`                       | Base URL returned after OAuth2/SAML redirects                  |
| `GITNODE_CORS_ALLOWED_ORIGINS`          |          | `http://localhost:4200,http://localhost:4300` | CORS origins — add admin panel URL for admin access            |
| `GITNODE_AUDIT_ENABLED`                 |          | `true`                                        | Application audit log                                          |
| `GITNODE_OBSERVABILITY_ENABLED`         |          | `true`                                        | Prometheus `/actuator/prometheus`                              |
| `GITNODE_ACTIONS_ENABLED`               |          | `true`                                        | CI/CD Actions engine                                           |
| `GITNODE_ADMIN_MODULITH_EVENTS_ENABLED` |          | `false`                                       | Admin panel event publication viewer                           |
| `GITNODE_ADMIN_PGAUDIT_ENABLED`         |          | `false`                                       | Admin panel pgAudit log viewer                                 |
| `GITNODE_ADMIN_PGAUDIT_LOG_DIRECTORY`   |          | —                                             | Postgres log dir mounted into the app container                |
| `GITNODE_SSO_SAML_ENABLED`              |          | `false`                                       | Global SAML feature flag                                       |
| `GITNODE_SSO_LDAP_ENABLED`              |          | `false`                                       | Global LDAP feature flag                                       |
| `GITNODE_SSO_SAML_SP_SIGNING_KEY_PATH`  |          | —                                             | SP signing private key path (SAML); `make saml-keygen`         |
| `GITNODE_SSO_SAML_SP_SIGNING_CERT_PATH` |          | —                                             | SP signing certificate path (SAML); `make saml-keygen`        |
| `ACTIONS_ENCRYPTION_KEY`                |          | —                                             | AES-256 key (base64) for Actions secrets vault; `make actions-encryption-key` |
| `GITNODE_AI_ENCRYPTION_KEY`             |          | —                                             | AES-256 key (base64) for user AI API keys at rest; `make ai-encryption-key` |
| `SPRING_DATA_REDIS_HOST`                |          | `localhost`                                   | Redis hostname                                                 |
| `SPRING_DATA_REDIS_PORT`                |          | `6379`                                        | Redis port                                                     |
| `SPRING_DATASOURCE_URL`                 |          | `jdbc:postgresql://localhost:5432/gitnode`    | Postgres JDBC URL                                              |
| `SPRING_DATASOURCE_USERNAME`            |          | `admin`                                       | Postgres username                                              |
| `SPRING_DATASOURCE_PASSWORD`            |          | `admin123`                                    | Postgres password                                              |
| `OAUTH2_GOOGLE_CLIENT_ID`               |          | —                                             | Google OAuth2 client ID                                        |
| `OAUTH2_GOOGLE_CLIENT_SECRET`           |          | —                                             | Google OAuth2 client secret                                    |
| `OAUTH2_GITHUB_CLIENT_ID`               |          | —                                             | GitHub OAuth2 client ID                                        |
| `OAUTH2_GITHUB_CLIENT_SECRET`           |          | —                                             | GitHub OAuth2 client secret                                    |
| `OAUTH2_GITLAB_CLIENT_ID`               |          | —                                             | GitLab OAuth2 client ID                                        |
| `OAUTH2_GITLAB_CLIENT_SECRET`           |          | —                                             | GitLab OAuth2 client secret                                    |

</details>

## Architecture

GitNode is a **Spring Modulith modular monolith**: each bounded context (repositories, pull requests, issues, tasks,
actions, identity, …) is a module with an explicit public API. Modules communicate through **domain events** for
reactions and through **ports** (hexagonal) for synchronous queries; persistence stays inside the owning module. The
rules are enforced by tests (`GitNodeArchitectureTest`).

See **[ARCHITECTURE.md](ARCHITECTURE.md)** for the module map, allowed dependencies and conventions.

| Layer | Technology |
|---|---|
| Backend | Java 25, Spring Boot 4, Spring Security, Spring Data JPA, Eclipse JGit, Apache MINA SSHD |
| Data | PostgreSQL 17 (Flyway, pgAudit), Redis 7 |
| Frontend | Angular 21, TypeScript, Tailwind CSS 4, DaisyUI 5 |
| CI/CD runner | Go 1.24 |
| Observability | Micrometer, Prometheus, Grafana, Resilience4j |

## Scaling and high availability

App instances are stateless and can run behind a load balancer: sessions are JWT-based, caches, rate-limit counters and
Actions runtime state live in Redis, webhook retry state lives in PostgreSQL. Two paths need **shared storage** when you
run more than one instance:

| Data | Setting |
|---|---|
| Git repositories | `GITNODE_GIT_REPO__ROOT` |
| CI/CD artifacts and cache | `gitnode.actions.artifacts.local-path` |

Mount a network filesystem (NFS, EFS, Filestore, Azure Files) or a `ReadWriteMany` PVC at both paths.

## Contributing

Contributions are welcome. Read **[CONTRIBUTING.md](CONTRIBUTING.md)** for the development workflow, run profiles and
test commands (`make test` for unit tests and lint, end-to-end tests under `e2e/`), and
**[ARCHITECTURE.md](ARCHITECTURE.md)** before adding or changing modules.

## License

Distributed under the [MIT License](LICENSE.txt).
