# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OriginHub is a self-hosted Git registry (GitHub alternative). Single Docker image serves both the Spring Boot API and the Angular SPA via static file serving.

- Backend: Java 25, Spring Boot 4, Spring Modulith, JGit, Apache MINA SSHD, PostgreSQL + Flyway
- Frontend: Angular 21, TypeScript 5, Tailwind CSS 4, DaisyUI 5
- Maven multi-module: `originhub-parent` → `originhub-backend`, `originhub-actions`

## Commands

### Backend

```bash
# Build (skips tests)
./mvnw clean package -DskipTests

# Build with full checks (checkstyle + fmt + spotbugs run at verify phase)
./mvnw clean verify

# Run tests only
./mvnw test

# Run single test class
./mvnw test -pl originhub-backend -Dtest=OriginHubArchitectureTest

# Format Java source (Google Java Format)
./mvnw com.spotify.fmt:fmt-maven-plugin:format

# Run locally (requires Postgres at localhost:5432)
./mvnw spring-boot:run -pl originhub-backend -Dspring-boot.run.profiles=local
```

### Frontend

```bash
cd originhub-frontend
pnpm install       # Node >=24, <25 required
pnpm start         # dev server at localhost:4200 (runs set-env.js first)
pnpm build         # production build
pnpm lint          # eslint
pnpm lint-fix      # eslint --fix
pnpm format        # prettier write
pnpm format:check  # prettier check
pnpm test          # karma unit tests
```

### Docker (production)

```bash
make up      # create network, start postgres + app
make down    # stop and remove containers
make logs    # follow app logs
make purge   # remove everything including volumes ⚠️
```

## Architecture

### Backend: Spring Modulith modules

Each top-level package under `com.nuricanozturk.originhub` is a Spring Modulith module. Module boundaries are enforced — cross-module access only via events or through the `shared` module.

| Module | Type | Purpose |
|--------|------|---------|
| `shared` | OPEN | Cross-cutting: Tenant/Repo entities, GitProvider, BranchService, error handling, RepoAccess interceptor |
| `pr` | OPEN | Pull requests; publishes events consumed by `task` |
| `auth` | closed | JWT + OAuth2 (Google/GitHub/GitLab), SSH key management |
| `repo` | closed | Repository CRUD, publishes events |
| `branch` | closed | Branch operations |
| `commit` | closed | Commit history and diffs |
| `tree` | closed | File tree and blob viewer via JGit; archive download |
| `snippet` | closed | Gist-like code snippets with revision history |
| `issue` | closed | Repo issues |
| `task` | closed | Projects, Kanban boards, tasks/subtasks; listens to PR and issue events |
| `migration` | closed | GitHub repo import (mirror clone + optional PR migration) |
| `profile` | closed | Public user profile + README |
| `ssh` | closed | Apache MINA SSHD server on port 2222 |

**Architecture test** at `OriginHubArchitectureTest` runs `ApplicationModules.verify()` — it will fail if you introduce illegal cross-module dependencies.

#### Within each module, the internal layout is:
- `controllers/` — REST endpoints
- `services/` — business logic
- `entities/` — JPA entities (only in modules that own their tables)
- `repositories/` — Spring Data JPA
- `dtos/` — request/response records
- `mappers/` — MapStruct interfaces
- `listeners/` — Spring Modulith `@ApplicationModuleListener` event handlers

#### Shared cross-module primitives live in `shared/`:
- `shared/repo` — `Repo` entity + `RepoService`, `RepoStorageService`
- `shared/tenant` — `Tenant` entity (user account)
- `shared/git/provider` — `GitProvider`: low-level JGit operations
- `shared/git/http` — HTTP Git smart backend (JGit servlet + auth filter)
- `shared/branch` — `BranchService`, `BranchProtocolService`
- `shared/configs` — `RepoAccessInterceptor` (enforces private repo access on all `/{owner}/{repo}/**` routes)

### Git transport

- **HTTP/HTTPS**: JGit `GitSmartHttpBackend` servlet mounted at `/git/**` via `HttpGitConfiguration`. Auth handled by `HttpGitAuthenticationFilter`.
- **SSH**: Apache MINA SSHD on port 2222. Host key stored at `${originhub.git.repo-root}/.hosts/ssh_host_key`.
- Repos stored on disk at `${ORIGINHUB_GIT_REPO__ROOT}` (default `~/.originhub`).

### Database

Flyway migrations in `originhub-backend/src/main/resources/db/migration/`. DDL is never auto-generated (`ddl-auto: none`). Add new migrations as `V00N__description.sql`.

Spring Modulith event persistence uses JDBC (`spring.modulith.events.jdbc`). The event log tables are created separately (not via Flyway).

### Frontend: Angular 21 standalone components

All routes use `loadComponent` (lazy). Layout:

- `core/` — services, guards, interceptors (one subfolder per domain)
- `domain/` — models and port interfaces (pure TypeScript types, no Angular deps)
- `features/` — page components, one folder per feature
- `shared/` — reusable components (`avatar`, `confirm-modal`, `toast`), pipes, utils
- `layout/` — `navbar`, `footer`

Guards: `authGuard` (requires login), `guestGuard` (redirects logged-in users), `repoOwnerGuard` (requires repo ownership), `redirectIfAuthGuard` (landing page redirect).

API base URL is injected via `set-env.js` into `src/environments/environment.ts` at build time.

### Code quality

- **Google Java Format** (`fmt-maven-plugin`) — enforced at `verify`. Run `./mvnw fmt:format` to auto-fix.
- **Checkstyle** (`config/checkstyle.xml`) — no JetBrains annotations (use `org.jspecify`), no tabs, newline at EOF.
- **Error Prone** — compiler plugin, active on main sources only.
- **SpotBugs** — static analysis at `verify`.
- Frontend: ESLint + Prettier + `eslint-plugin-simple-import-sort`.

### Local dev profile

Use `application-local.yaml` (`-Dspring.profiles.active=local`) for local development — has hardcoded DB, JWT secret, and OAuth2 credentials pre-filled.
