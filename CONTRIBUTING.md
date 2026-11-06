# Contributing to GitNode

How to run GitNode locally and what a pull request has to pass.

## Prerequisites

Java **25** · Node **24** · pnpm **10** · Docker · Go **1.24** (runner only)

```bash
corepack enable && corepack prepare pnpm@10 --activate
```

Use `./mvnw` (Maven is bundled).

---

## Run locally

### Base app — Frontend + Backend

**1. Once per clone:**

```bash
make dev-setup
```

It starts Postgres and Redis (Docker), installs the shared events module the backend depends on, runs `pnpm install`
in `gitnode-frontend`, `gitnode-admin-panel` and `e2e`, and writes `.env.local` (gitignored) with the backend settings:

| Variable                           | Why                                                                                     |
|------------------------------------|-----------------------------------------------------------------------------------------|
| `GITNODE_JWT_SECRET`               | Required, no default. Signs login tokens: base64 that decodes to at least 32 bytes (`openssl rand -base64 32`). |
| `GITNODE_BOOTSTRAP_ADMIN_PASSWORD` | Creates the user `admin` on first start (`Admin123`). Without it no admin exists (a warning is logged). To log in, a password needs a digit, a lowercase and an uppercase letter. |
| `ACTIONS_ENCRYPTION_KEY`           | Needed for Actions secrets (the e2e secrets test fails without it).                     |

Postgres and Redis defaults match `make infra` (`localhost:5432`, `admin` / `admin123`, `localhost:6379`).
Git data goes to `~/.gitnode`; set `GITNODE_GIT_REPO__ROOT` to use another directory.

**2. Start the backend** (it loads `.env.local` itself):

```bash
make dev-backend            # http://localhost:8080, SSH on :2222
```

Without the Makefile: export the variables from `.env.local`, run `make infra` and
`./mvnw install -DskipTests -pl gitnode-events` once, then
`./mvnw spring-boot:run -pl gitnode-backend -Dspring-boot.run.profiles=local`.

**3. Start the frontend** (second terminal):

```bash
cd gitnode-frontend
pnpm install
pnpm start                  # http://localhost:4200
```

Log in with `admin` / `Admin123` (or the password you set).

### Full app — + Admin

The admin API is always on in the backend. Start the admin panel as well (third terminal):

```bash
cd gitnode-admin-panel
pnpm install
pnpm start                  # http://localhost:4300
```

Log in with the same `admin` account.

---

## Run in Docker

```bash
make up                     # Postgres + Redis + the published GitNode image → http://localhost:8080, SSH :2222
make stop                   # stop the containers, keep the data
make start                  # start them again
make down                   # remove the containers: this also deletes the database
make purge                  # down + delete volumes and network
```

`make up` runs the image named `IMAGE` in the `Makefile`, **not your working tree**. It sets no admin password,
so no `admin` user exists. To test your own code use the local run above.

---

## Before you open a pull request

CI (`.github/workflows/mr-check.yml`) runs these. Run them locally first.

| Area                              | Command                                                                          |
|-----------------------------------|----------------------------------------------------------------------------------|
| Backend + events                  | `./mvnw verify` (tests, architecture rules, checkstyle, format check)            |
| Backend format (fixes your code)  | `./mvnw com.spotify.fmt:fmt-maven-plugin:format`                                 |
| Runner                            | `cd gitnode-runner && make fmt-check test lint build`                            |
| Frontend, admin panel, e2e        | `pnpm install`, then `pnpm lint` in each of the three folders                    |
| Frontend unit tests               | `cd gitnode-frontend && pnpm test --watch=false`                                 |
| Whitespace and line endings       | `.editorconfig`: UTF-8, LF, 2 spaces (tabs in Go), newline at end of file        |

Shortcuts: `make verify` is `./mvnw verify`. `make test` runs the backend tests, the runner tests and all three
lints, with no server needed.

---

## Architecture rules

The backend is a modular monolith ([Spring Modulith](https://spring.io/projects/spring-modulith)). Each folder under
`gitnode-backend/src/main/java/dev/gitnode/os/` (`actions`, `repo`, `pr`, `tag`, ...) is a module.
`GitNodeArchitectureTest` enforces the boundaries and fails the build when you cross them.

- **A module may only use the modules listed in `allowedDependencies`** in its `package-info.java`. Do not add an
  entry just to make the build pass: reach the other module through its `api` package or an event instead.
- **Modules talk through `api` packages and domain events.** Events are plain records in the `gitnode-events`
  module, grouped in named interfaces (`events :: repo`, `events :: tag`, ...). A new cross-module reaction usually
  means a new event record there.
- Controllers never use repositories. Services never use controllers.
- An `api` package never exposes JPA entities.
- The `repo` and `identity` repositories may only be used by `repo`, `identity`, `githttp`, `ssh`, `auth`, `profile`
  and `admin`. Other modules go through the `api` ports. Known exceptions are frozen in
  `src/test/resources/archunit_store`; new violations fail.
- `gitnode-events` records are plain data: they must not depend on `shared`, `pr`, `issue` or `auth`.

Run only the boundary check: `./mvnw test -pl gitnode-backend -Dtest=GitNodeArchitectureTest`.

### Code style (checked by `./mvnw verify`)

- Formatting is google-java-format through `fmt-maven-plugin`: run the format command above, do not hand-format.
- Checkstyle (`config/checkstyle.xml`) fails the build on, among others: cyclomatic complexity above 7, magic
  numbers, non-`final` parameters, member access without `this.`, star imports, lines over 120 characters,
  one-letter `catch` variable names, `System.out`/`System.err.println`, JetBrains annotations (use `org.jspecify`) and JUnit 4.
- Database changes are Flyway migrations: `gitnode-backend/src/main/resources/db/migration/V0NN__description.sql`.

---

## Tests

```bash
make test                          # backend + runner tests and lints, no server needed
```

**E2E** (Playwright, HTTP only) needs the backend running as in [Run locally](#run-locally), with
`GITNODE_BOOTSTRAP_ADMIN_PASSWORD=Admin123` and `ACTIONS_ENCRYPTION_KEY` set. The tests log in as `admin` / `Admin123`.

```bash
cd e2e
pnpm install
pnpm test:e2e:api                  # REST tests only
pnpm test:e2e                      # API, scenario, teardown (needs git on PATH)
```

See [e2e/README.md](e2e/README.md) for SAML and LDAP runs.

---

## Runner

The CI/CD agent is a Go program in `gitnode-runner/`.

```bash
make runner-build                  # → gitnode-runner/dist/gitnode-runner
make test-runner                   # Go tests
```

Generate a registration token in the web UI (**Settings → Actions → Self-hosted runners**), then:

```bash
./gitnode-runner/dist/gitnode-runner start --server-url http://localhost:8080 --token <token> --labels self-hosted,linux
```

`make runner-build-all` builds Linux amd64/arm64, macOS arm64 and Windows amd64.

---

## Useful Make targets

Run `make help` for all of them.

| Command                       | Does                                                         |
|-------------------------------|--------------------------------------------------------------|
| `make infra` / `infra-down`   | Start / remove Postgres and Redis                            |
| `make infra-stop` / `infra-start` | Stop / start them without removing                      |
| `make logs-db`, `logs-redis`  | Follow Postgres / Redis logs                                 |
| `make ps`                     | List GitNode containers                                      |
| `make ldap-up` / `ldap-down`  | OpenLDAP test server for the LDAP e2e                        |
| `make saml-keygen`            | SAML signing keys in `~/.gitnode/saml/`                      |
| `make actions-encryption-key` | New Actions secrets key in `~/.gitnode/actions-encryption-key` |
| `make ai-encryption-key`      | New AI key encryption key in `~/.gitnode/ai-encryption-key`  |

---

## Repo layout

```
gitnode/
├── gitnode-backend/      API + Git (Spring Boot)
├── gitnode-events/       Shared domain events
├── gitnode-frontend/     Main UI (Angular, :4200)
├── gitnode-admin-panel/  Platform admin UI (Angular, :4300)
├── gitnode-runner/       CI/CD agent (Go)
├── e2e/                  Playwright tests
├── config/               Checkstyle rules
├── docs/                 Actions compatibility
├── docker-compose.yml    Postgres + Redis (+ runner profile)
└── Makefile              Commands above
```

## Module READMEs

| Path                                                           | What                    |
|----------------------------------------------------------------|-------------------------|
| [gitnode-backend/README.md](gitnode-backend/README.md)         | Backend                 |
| [gitnode-frontend/README.md](gitnode-frontend/README.md)       | Angular app scripts     |
| [gitnode-admin-panel/README.md](gitnode-admin-panel/README.md) | Admin UI                |
| [gitnode-runner/README.md](gitnode-runner/README.md)           | CI/CD runner agent      |
| [e2e/README.md](e2e/README.md)                                 | Playwright tests        |
| [docs/actions-compatibility.md](docs/actions-compatibility.md) | GitHub Actions support  |

## Memory diagnostics

The container JVM writes a heap dump (`/var/log/gitnode/*.hprof`) and exits on `OutOfMemoryError`; GC logs rotate at `/var/log/gitnode/gc.log`. Mount that path as a volume to keep them.

```bash
docker exec <ctr> jcmd 1 GC.heap_info
docker exec <ctr> jcmd 1 JFR.start duration=60s filename=/var/log/gitnode/rec.jfr
```

The JGit pack window cache is capped via `gitnode.git.window-cache.packed-git-limit-mb` (default 128) and `gitnode.git.window-cache.delta-base-cache-limit-mb` (default 16).

## Adding a module

1. Create `dev.gitnode.os.<module>` with `package-info.java` (`@ApplicationModule(allowedDependencies = {...})`, every entry an explicit `module :: namedInterface`).
2. Expose what others need only through `<module>.api` (annotate the package `@NamedInterface("api")`) and events through `dev.gitnode.os.events.<module>`.
3. Keep entities, repositories and services package-internal in spirit: reference other modules by UUID and call their ports.
4. Add the module to the layer table in `ModuleBoundaryRulesTest` (it may only depend on lower layers).
5. Any column that references `tenant` or `repositories` gets a foreign key with `ON DELETE CASCADE` (or `SET NULL`) and an index in a Flyway migration.
6. Run `./mvnw verify`; the architecture tests explain any violation. See `docs/adr/0001-modular-monolith-rules.md`.
