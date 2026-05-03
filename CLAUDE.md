# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick Start

**Backend (Java/Spring Boot 4)**
```bash
mvn clean install              # Build with checkstyle, error-prone, spotbugs
mvn test                       # Run tests
mvn test -Dtest=ClassName     # Single test class
mvn clean verify               # Full verification (tests + static analysis)
mvn spring-boot:run            # Run locally (requires DB, see below)
```

**Frontend (Angular 21)**
```bash
cd originhub-frontend
pnpm install                   # Install dependencies
pnpm start                     # Dev server at http://localhost:4200
pnpm build                     # Production build
pnpm test                      # Run Karma tests
pnpm lint                      # ESLint + Angular lint
pnpm lint-fix                  # Auto-fix lint issues
pnpm format                    # Prettier formatting
```

**Docker (simplest)**
```bash
make up                        # Start PostgreSQL + app at localhost:8080
make down                      # Stop containers
make logs                      # Follow app logs
```

---

## Architecture

### Backend: Spring Modulith (Modular Monolith)

Organized by feature module under `originhub-backend/src/main/java/com/nuricanozturk/originhub/`:

- **auth** — JWT + OAuth2 (Google, GitHub, GitLab), login/register, token validation
- **repo** — Git repository CRUD, clone/push/pull via JGit, metadata storage
- **ssh** — SSH server (Apache MINA SSHD) on port 2222 for git over SSH
- **branch** — Branch listing, creation, deletion
- **commit** — Commit history, diff rendering, commits in trees
- **pr** — Pull request management (create, review, merge—merge commit/squash/rebase)
- **tree** — File tree navigation, syntax highlighting (highlight.js), markdown rendering
- **profile** — User profiles, activity
- **shared** — Common utilities (Git helpers, HTTP configuration, base entities)

**Key patterns:**
- Spring Modulith for loose coupling + event-driven sync between modules
- Spring Data JPA with PostgreSQL (Flyway migrations in `originhub-backend/src/main/resources/db/migration/`)
- Checkstyle + Error-Prone compiler checks + SpotBugs (run automatically on `mvn verify`)
- MapStruct for DTOs

### Frontend: Angular Standalone + Tailwind

`originhub-frontend/src/app/`:

- **core** — HTTP interceptors, auth guards, JWT token management
- **domain** — Strongly-typed services (RepoService, AuthService, etc.)
- **features** — Feature routes (auth, repo, pr, profile, etc.)
- **shared** — Reusable components, pipes, directives
- **layout** — Navigation shell, footer

**Key patterns:**
- Standalone components (no NgModules)
- Reactive HTTP (RxJS)
- Routing with route guards
- Tailwind + DaisyUI for styling
- Lucide icons

---

## Database

**Development:**
```bash
# Option 1: Docker (in Makefile)
make up           # Starts PostgreSQL 17

# Option 2: Manual PostgreSQL (if you prefer local)
createdb originhub
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/originhub"
export SPRING_DATASOURCE_USERNAME="admin"
export SPRING_DATASOURCE_PASSWORD="admin123"
```

Flyway auto-migrations run on startup. Schema: `db/migration/` files.

---

## Code Quality

All run on `mvn verify`:

1. **Checkstyle** (`config/checkstyle.xml`) — style enforcement
2. **Error-Prone** — compile-time bug detection
3. **SpotBugs** — bytecode static analysis
4. **Maven Formatter** — Java code format check (no auto-fix; use IDE)
5. **Maven Surefire** — unit tests

Frontend:
- ESLint with prettier plugin
- No pre-commit hooks enforced (manual via `pnpm lint-fix` / `pnpm format`)

---

## Git Protocols

App supports both:

1. **SSH** — port 2222 (handled by `ssh` module, Apache MINA SSHD)
2. **HTTP(S)** — port 8080 (JGit HTTP servlet + custom auth in `shared/git/provider/`)

Recent addition: `HttpGitConfiguration.java` enables HTTPS clones over HTTP with JWT auth.

---

## Environment Variables

**Backend (Spring Boot):**
```
SPRING_DATASOURCE_URL          # PostgreSQL JDBC URL
SPRING_DATASOURCE_USERNAME     # DB user
SPRING_DATASOURCE_PASSWORD     # DB password
ORIGINHUB_JWT_SECRET           # Min 32 chars for HS256
ORIGINHUB_GIT_REPO__ROOT       # Path to store git repos (e.g., /data/repos)
SPRING_PROFILES_ACTIVE         # "os" for self-hosted (production profile)
OAUTH2_GOOGLE_CLIENT_ID        # Optional OAuth2 keys
OAUTH2_GOOGLE_CLIENT_SECRET
OAUTH2_GITHUB_CLIENT_ID
OAUTH2_GITHUB_CLIENT_SECRET
OAUTH2_GITLAB_CLIENT_ID
OAUTH2_GITLAB_CLIENT_SECRET
```

**Frontend (`set-env.js` reads `.env` at build time):**
```
NG_APP_API_URL     # Backend API base URL (default: http://localhost:8080/api)
```

---

## Testing

**Backend:**
- JUnit 5 in `src/test/java/`
- No separate test database config; tests use embedded or in-memory (check parent pom)
- Run single test: `mvn test -Dtest=ClassName#methodName`

**Frontend:**
- Karma + Jasmine in `src/app/**/*.spec.ts`
- Run: `pnpm test`

---

## Common Tasks

**Add a new REST endpoint:**
1. Create service in `repo/domain/` or module's domain
2. Create REST controller in `repo/presentation/` 
3. Attach Spring Security permissions if needed (auth module)
4. Test with `mvn test`

**Add a frontend page:**
1. Create feature folder in `features/`
2. Add route in `app.routes.ts`
3. Use services from `domain/` to call backend
4. Import shared components from `shared/`

**Update database schema:**
1. Add migration file in `originhub-backend/src/main/resources/db/migration/V<N>__<desc>.sql`
2. Flyway runs automatically on startup

**Fix code style:**
```bash
# Backend: use IDE formatter or check what checkstyle wants
mvn checkstyle:checkstyle    # Report only
# Frontend:
pnpm lint-fix && pnpm format
```

---

## Known Issues & Context

- **HTTP/HTTPS support** added recently (`HttpGitConfiguration.java`) for git clone over HTTP
- JWT secret is environment-required; .env.example provides template
- Frontend uses DaisyUI for pre-built components (Tailwind wrapper)
- No Actions (CI/CD) yet—marked "coming soon" in README
