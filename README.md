<div align="center">

<h1><img src="images/gitnode-mark.svg" width="44" valign="middle" /> &nbsp;<b>Git</b>Node</h1>

<p><b>A self-hosted Git platform — repositories, code review, CI/CD and project management on your own infrastructure.</b></p>

[![Release](https://img.shields.io/github/v/release/nuricanozturk01/gitnode?style=flat-square)](https://github.com/nuricanozturk01/gitnode/releases/latest)
[![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-21-DD0031?style=flat-square&logo=angular)](https://angular.dev)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-336791?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square)](LICENSE.txt)
<a href="https://status.gitnode.dev" target="_blank" rel="noopener noreferrer"><img src="https://api.cronbeat.dev/badge/A1HG1-tT7Jvw49lO_2d8tmVU/uptime.svg?range=90d" alt="GitNode uptime"></a>

[Run it](#run-it) · [CI/CD runner](#cicd-runner) · [Features](#features) · [Screenshots](#screenshots) · [Architecture](#architecture) · [Production](#production) · [Configuration](#configuration) · [Contributing](CONTRIBUTING.md)

</div>

---

GitNode is a self-hosted Git platform: repositories, pull requests, issues, CI/CD and project boards on your own
server. One Spring Boot backend, an Angular web app, an optional admin panel and an optional Go runner for CI/CD.
In-app documentation is served at `/docs`.

## Run it

Needs [Docker](https://docs.docker.com/get-docker/) and `make`.

```bash
make up
```

Open <http://localhost:8080> and register an account. Git over SSH is on `localhost:2222`.

| Command | Does |
|---|---|
| `make stop` / `make start` | Stop and start again, data is kept |
| `make down` | Remove the containers. **This deletes the database** |
| `make purge` | `make down` plus all volumes and the network: deletes all data |

`make up` runs the published GitNode image with throwaway credentials from the `Makefile` and creates no admin user.
For a real deployment see [Production](#production). To work on the code, see [CONTRIBUTING.md](CONTRIBUTING.md).

### One command, no setup

The image brings its own PostgreSQL and Redis, so a single `docker run` is enough:

```bash
docker run -d --name gitnode -p 8080:8080 -p 2222:2222 -v gitnode-data:/data \
  repo.repsy.io/nuricanozturk/gitnode/gitnode-os:latest
docker logs gitnode   # first start prints the generated admin password
```

Open <http://localhost:8080> and sign in as `admin`. Everything (repositories, database, generated secrets) lives in the `gitnode-data` volume, so removing and re-running the container keeps your data. Set `GITNODE_BOOTSTRAP_ADMIN_PASSWORD` to choose the admin password yourself.

Set `SPRING_DATASOURCE_URL` and/or `SPRING_DATA_REDIS_HOST` to use your own PostgreSQL / Redis; the embedded one is then not started. This image is built for `http://localhost:8080`; for another host name build with `--build-arg API_URL=https://your-host`.

## CI/CD runner

Workflows live in `.gitnode/workflows/` and run on a self-hosted runner. In the web UI open
**Settings → Actions → Self-hosted runners** and generate a token. The page shows this command ready to copy:

```bash
docker run -d --init --name my-runner --restart unless-stopped \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v gitnode-runner-data:/data \
  repo.repsy.io/nuricanozturk/gitnode-runner/gitnode-runner:latest \
  --server-url <GitNode URL> --token <token> --name my-runner --labels self-hosted,linux
```

`--server-url` must be reachable from inside the container, so not `localhost`. As a plain binary instead:
`make runner-build`, then `./gitnode-runner/dist/gitnode-runner start --server-url <GitNode URL> --token <token>`.

Workflows are meant to run unchanged from GitHub Actions. What differs:

- **Reusable workflows (`workflow_call`) are not supported yet.** The calling job fails with a clear message.
- **No GitHub-hosted runners.** `ubuntu-latest` is not translated; use `runs-on: [self-hosted, linux]`.
- **No `GITHUB_TOKEN`.** Store a token as a secret.

Full list: [docs/actions-compatibility.md](docs/actions-compatibility.md).

## Features

| Area | Highlights |
|---|---|
| **Repositories** | Public and private repos, forks, branch protection, tags and releases, Git over HTTP(S) and SSH |
| **Code review** | Pull requests (merge, squash, rebase), reviewers, approvals, inline comments, required checks, CODEOWNERS, auto-merge |
| **Planning** | Issues with labels and milestones, project boards with tasks |
| **CI/CD** | YAML workflows, matrix jobs, encrypted secrets, artifacts, live logs, self-hosted runners |
| **Collaboration** | Collaborator roles, teams, Git-backed wiki, discussions, snippets, signed webhooks with retries, notifications |
| **Security** | JWT, OAuth2 (Google, GitHub, GitLab), SAML 2.0, LDAP, two-factor (TOTP), SSH keys, rate limiting, audit log |
| **AI (opt-in)** | PR review and text suggestions with your own key: OpenAI, Anthropic, Gemini, Ollama |
| **Operations** | Admin panel, GitHub import, health and metrics endpoints (Spring Actuator) |

## Screenshots

<p align="center">
  <img src="images/screenshots/dashboard/01-dashboard.png" alt="GitNode dashboard" width="900">
</p>

### Repositories

<table>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/repository/03-code-browser.png" alt="Code browser">
      <br><sub><b>Code browser.</b> Files, README, languages, tags and the latest release.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="images/screenshots/repository/05-file-view.png" alt="File view">
      <br><sub><b>File view.</b> Syntax highlighting, copy, and edit in the browser.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/repository/06b-web-editor-fullscreen.png" alt="Web editor, full screen">
      <br><sub><b>Web editor, full screen.</b> File tree, tabs and commit, with the whole window to write in.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="images/screenshots/repository/09-commit-diff.png" alt="Commit diff">
      <br><sub><b>Commit diff.</b> What a commit changed, line by line.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/repository/07-branches.png" alt="Branches">
      <br><sub><b>Branches.</b> Branch list with the default and protected branches.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="images/screenshots/repository/10-releases.png" alt="Releases">
      <br><sub><b>Releases.</b> Tagged releases with notes.</sub>
    </td>
  </tr>
</table>

### Code review

<table>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/code-review/01-pull-requests.png" alt="Pull requests">
      <br><sub><b>Pull requests.</b> Open, merged and closed pull requests at a glance.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="images/screenshots/code-review/02-pull-request-conversation.png" alt="Pull request activity">
      <br><sub><b>Pull request activity.</b> Reviewers, comments and merge requirements.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/code-review/03-pull-request-files.png" alt="Files changed">
      <br><sub><b>Files changed.</b> The diff of the pull request, ready to review.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="images/screenshots/code-review/05-pull-request-draft.png" alt="Draft pull request">
      <br><sub><b>Draft pull request.</b> Work in progress, not ready for review yet.</sub>
    </td>
  </tr>
</table>

### Planning

<table>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/planning/01-issues.png" alt="Issues">
      <br><sub><b>Issues.</b> Labels, milestones and assignees.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="images/screenshots/planning/02-issue-detail.png" alt="Issue">
      <br><sub><b>Issue.</b> Description, discussion and metadata.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/planning/05-project-board.png" alt="Project board">
      <br><sub><b>Project board.</b> Kanban board with columns and tasks.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="images/screenshots/planning/06-task-detail.png" alt="Task">
      <br><sub><b>Task.</b> A task linked to an issue and a branch.</sub>
    </td>
  </tr>
</table>

### Collaboration

<table>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/collaboration/02-discussion-answered.png" alt="Discussions">
      <br><sub><b>Discussions.</b> Q&A with an accepted answer.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="images/screenshots/collaboration/04-wiki-home.png" alt="Wiki">
      <br><sub><b>Wiki.</b> A Git-backed wiki with page history.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/collaboration/08-snippet-detail.png" alt="Snippets">
      <br><sub><b>Snippets.</b> Share code, with revisions and comments.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="images/screenshots/collaboration/09-team.png" alt="Team">
      <br><sub><b>Team.</b> Invite people and set their roles.</sub>
    </td>
  </tr>
</table>

### CI/CD: Actions

<table>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/actions/01-workflows.png" alt="Workflows">
      <br><sub><b>Workflows.</b> Every workflow and its runs, with filters.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="images/screenshots/actions/02-run-summary.png" alt="Run summary">
      <br><sub><b>Run summary.</b> Trigger, actor, duration and the job graph.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/actions/03-run-live-logs.png" alt="Live logs">
      <br><sub><b>Live logs.</b> Step logs stream while the job runs.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="images/screenshots/actions/04-run-finished.png" alt="Finished run">
      <br><sub><b>Finished run.</b> Build and push to a registry, with secrets masked.</sub>
    </td>
  </tr>
</table>

### Account and security

<table>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/account/01-profile.png" alt="Profile">
      <br><sub><b>Profile.</b> Contribution graph and your repositories, projects and snippets.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="images/screenshots/account/08-notifications.png" alt="Notifications">
      <br><sub><b>Notifications.</b> Reviews, comments and assignments in one place.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/account/04-settings-ssh-keys.png" alt="SSH keys">
      <br><sub><b>SSH keys.</b> Push over SSH with your own keys.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="images/screenshots/account/03-settings-security.png" alt="Security">
      <br><sub><b>Security.</b> Password and two-factor authentication.</sub>
    </td>
  </tr>
</table>

### Admin panel

<table>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/admin/01-dashboard.png" alt="Dashboard">
      <br><sub><b>Dashboard.</b> Platform statistics and activity.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="images/screenshots/admin/02-users.png" alt="Users">
      <br><sub><b>Users.</b> Search, disable and manage accounts.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/admin/06-organization-sso.png" alt="Organizations">
      <br><sub><b>Organizations.</b> Per-organization SAML single sign-on and LDAP.</sub>
    </td>
    <td width="50%" valign="top">
      <img src="images/screenshots/admin/07-system-health.png" alt="System health">
      <br><sub><b>System health.</b> Live status of the database, cache and core services.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <img src="images/screenshots/admin/08-audit-logs.png" alt="Audit log">
      <br><sub><b>Audit log.</b> Who did what, and when.</sub>
    </td>
    <td width="50%"></td>
  </tr>
</table>

More pages (settings, imports, wiki history, release details, ...) are in [`images/screenshots/`](images/screenshots).

## Architecture

<details>
<summary><b>Diagram</b></summary>

```mermaid
flowchart TD

subgraph group_clients["User interfaces"]
  node_web["Web app<br/>[app.routes.ts]"]
  node_adminui["Admin panel<br/>[app.routes.ts]"]
end

subgraph group_core["Git platform"]
  node_backend["Backend API"]
  node_identity["Identity and access<br/>[AuthService.java]"]
  node_repositories["Repositories"]
  node_review["Pull request review"]
  node_planning["Issues and projects<br/>[IssueService.java]"]
  node_collaboration["Collaboration"]
  node_gitstore[("Git repository files")]
end

subgraph group_delivery["CI and AI"]
  node_actions["Actions workflows"]
  node_runner["Go runner agent<br/>[main.go]"]
  node_ai["AI assistance"]
end

subgraph group_operations["Administration"]
  node_admin["Admin services"]
  node_database[("PostgreSQL")]
end

node_user(("Platform user"))
node_ai_provider["AI providers"]
node_ldap["LDAP and SSO"]

node_user -->|"uses"| node_web
node_user -->|"administers"| node_adminui
node_web -->|"uses API"| node_backend
node_adminui -.->|"uses API"| node_backend
node_web -->|"authenticates"| node_identity
node_identity -->|"supports login"| node_ldap
node_backend -->|"serves"| node_repositories
node_repositories -->|"stores Git data"| node_gitstore
node_backend -->|"serves"| node_review
node_backend -->|"serves"| node_planning
node_backend -->|"serves"| node_collaboration
node_backend -->|"runs workflows"| node_actions
node_actions -->|"dispatches jobs"| node_runner
node_backend -->|"offers assistance"| node_ai
node_ai -->|"requests completion"| node_ai_provider
node_adminui -->|"manages platform"| node_admin

click node_web "https://github.com/nuricanozturk01/gitnode/blob/main/gitnode-frontend/src/app/app.routes.ts"
click node_adminui "https://github.com/nuricanozturk01/gitnode/blob/main/gitnode-admin-panel/src/app/app.routes.ts"
click node_backend "https://github.com/nuricanozturk01/gitnode/blob/main/gitnode-backend/src/main/java/dev/gitnode/os/GitNodeApplication.java"
click node_identity "https://github.com/nuricanozturk01/gitnode/blob/main/gitnode-backend/src/main/java/dev/gitnode/os/auth/services/AuthService.java"
click node_repositories "https://github.com/nuricanozturk01/gitnode/blob/main/gitnode-backend/src/main/java/dev/gitnode/os/repo/services/RepoStorageService.java"
click node_review "https://github.com/nuricanozturk01/gitnode/blob/main/gitnode-backend/src/main/java/dev/gitnode/os/pr/services/PrReviewService.java"
click node_planning "https://github.com/nuricanozturk01/gitnode/blob/main/gitnode-backend/src/main/java/dev/gitnode/os/issue/services/IssueService.java"
click node_collaboration "https://github.com/nuricanozturk01/gitnode/blob/main/gitnode-backend/src/main/java/dev/gitnode/os/collaborator/services/CollaboratorService.java"
click node_actions "https://github.com/nuricanozturk01/gitnode/blob/main/gitnode-backend/src/main/java/dev/gitnode/os/actions/services/WorkflowExecutionService.java"
click node_runner "https://github.com/nuricanozturk01/gitnode/blob/main/gitnode-runner/cmd/runner/main.go"
click node_ai "https://github.com/nuricanozturk01/gitnode/blob/main/gitnode-backend/src/main/java/dev/gitnode/os/ai/services/AiCodeReviewService.java"
click node_admin "https://github.com/nuricanozturk01/gitnode/blob/main/gitnode-backend/src/main/java/dev/gitnode/os/admin/controllers/AdminStatsController.java"

classDef toneNeutral fill:#f8fafc,stroke:#334155,stroke-width:1.5px,color:#0f172a
classDef toneBlue fill:#dbeafe,stroke:#2563eb,stroke-width:1.5px,color:#172554
classDef toneAmber fill:#fef3c7,stroke:#d97706,stroke-width:1.5px,color:#78350f
classDef toneMint fill:#dcfce7,stroke:#16a34a,stroke-width:1.5px,color:#14532d
classDef toneRose fill:#ffe4e6,stroke:#e11d48,stroke-width:1.5px,color:#881337
classDef toneIndigo fill:#e0e7ff,stroke:#4f46e5,stroke-width:1.5px,color:#312e81
classDef toneTeal fill:#ccfbf1,stroke:#0f766e,stroke-width:1.5px,color:#134e4a
class node_web,node_adminui,node_user toneBlue
class node_backend,node_identity,node_repositories,node_review,node_planning,node_collaboration,node_gitstore toneAmber
class node_actions,node_runner,node_ai toneMint
class node_admin,node_database toneRose
class node_ai_provider,node_ldap toneIndigo
```

</details>

GitNode is a **Spring Modulith modular monolith**. Each area (repositories, pull requests, issues, actions, identity,
...) is a module with a public API. Modules react to each other through **domain events** and ask each other through
**ports**; persistence stays inside the owning module. `GitNodeArchitectureTest` enforces this, see
[CONTRIBUTING.md](CONTRIBUTING.md#architecture-rules).

| Layer | Technology |
|---|---|
| Backend | Java 25, Spring Boot 4, Spring Security, JPA, Eclipse JGit, Apache MINA SSHD |
| Data | PostgreSQL 18 (Flyway), Redis 7 |
| Frontend | Angular 21, Tailwind CSS 4, DaisyUI 5 |
| Runner | Go 1.24 |

## Production

`make up` is for trying GitNode out. For a real deployment:

1. **Set your own secrets.** `GITNODE_JWT_SECRET` (base64, at least 32 bytes: `openssl rand -base64 32`),
   `GITNODE_BOOTSTRAP_ADMIN_PASSWORD` (creates the first admin user, named `admin` by default; without it no admin exists), the database
   password, and the two encryption keys (`make actions-encryption-key`, `make ai-encryption-key`). Keep the keys
   stable: changing one makes the data it encrypted unreadable.
2. **Put HTTPS in front** (a reverse proxy such as Caddy or nginx) and set `GITNODE_FRONTEND_BASE_URL` and
   `GITNODE_CORS_ALLOWED_ORIGINS` to the public URLs. Expose port `2222` for Git over SSH if you use it.
3. **Persist and back up** PostgreSQL and the repository directory (`GITNODE_GIT_REPO__ROOT`). Back both up together.
4. **Turn on only what you use.** SAML and LDAP stay off until `GITNODE_SSO_SAML_ENABLED` /
   `GITNODE_SSO_LDAP_ENABLED` are `true`. Health: `/actuator/health`; metrics: `/actuator/metrics` (needs a login).

The `app` target in the [`Makefile`](Makefile) is a working example of the container and its environment.

## Configuration

Everything is an environment variable. The ones you will almost always set:

| Variable | Description |
|---|---|
| `GITNODE_JWT_SECRET` | **Required.** Base64 secret for signing JWTs, at least 32 bytes decoded |
| `GITNODE_BOOTSTRAP_ADMIN_PASSWORD` | Password of the first admin user |
| `GITNODE_GIT_REPO__ROOT` | Storage path for Git repositories (mount a volume) |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | PostgreSQL connection |
| `SPRING_DATA_REDIS_HOST` / `_PORT` | Redis connection |
| `ACTIONS_ENCRYPTION_KEY`, `GITNODE_AI_ENCRYPTION_KEY` | Base64 AES-256 keys for the secrets vault and stored AI keys |
| `GITNODE_FRONTEND_BASE_URL`, `GITNODE_CORS_ALLOWED_ORIGINS` | Public URLs for OAuth/SAML redirects and CORS |

<details>
<summary><b>Full variable reference</b></summary>

| Variable                                | Required | Default                                       | Description                                                    |
|-----------------------------------------|----------|-----------------------------------------------|----------------------------------------------------------------|
| `GITNODE_JWT_SECRET`                    | ✅        | —                                             | Base64 secret for JWT signing, at least 32 bytes decoded (`openssl rand -base64 32`) |
| `GITNODE_BOOTSTRAP_ADMIN_PASSWORD`      | ✅ prod   | —                                             | Bootstrap admin password (empty skips bootstrap)               |
| `GITNODE_BOOTSTRAP_ADMIN_USERNAME`      |          | `admin`                                       | First-start platform admin username                            |
| `GITNODE_BOOTSTRAP_ADMIN_ENABLED`       |          | `true`                                        | Run bootstrap admin creation on startup                        |
| `GITNODE_PLATFORM_ADMIN_USERNAMES`      |          | —                                             | Comma-separated additional platform admin usernames            |
| `GITNODE_GIT_REPO__ROOT`                |          | `~/.gitnode`                                  | Git repository storage path (use a volume in Docker)           |
| `GITNODE_FRONTEND_BASE_URL`             |          | `http://localhost:4200`                       | Base URL returned after OAuth2/SAML redirects                  |
| `GITNODE_CORS_ALLOWED_ORIGINS`          |          | `http://localhost:4200,http://localhost:4300` | CORS origins — add admin panel URL for admin access            |
| `GITNODE_AUDIT_ENABLED`                 |          | `true`                                        | Application audit log                                          |
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

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[Apache License 2.0](LICENSE.txt)
