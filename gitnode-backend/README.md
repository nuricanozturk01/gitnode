# GitNode Backend

Spring Boot API + Git HTTP/SSH. Production image embeds the built frontend.

Part of **base app**. Run profile: [CONTRIBUTING.md](../CONTRIBUTING.md#base-app--frontend--backend)

## Run

```bash
make dev-setup      # once: Postgres + Redis, events module, .env.local, pnpm install
make dev-backend    # → http://localhost:8080, SSH :2222
```

`make dev-setup` writes `.env.local` (gitignored) with:
`GITNODE_JWT_SECRET` (required), `GITNODE_BOOTSTRAP_ADMIN_PASSWORD` (creates the `admin` user) and `ACTIONS_ENCRYPTION_KEY` (Actions secrets).
`make dev-backend` loads it. To run without the Makefile, export the same variables and run
`./mvnw spring-boot:run -pl gitnode-backend -Dspring-boot.run.profiles=local`.
See [CONTRIBUTING.md](../CONTRIBUTING.md#base-app--frontend--backend).

## Admin API

Always on under `/api/admin/**`; there is no switch to turn it off. It serves the
[admin panel](../gitnode-admin-panel/README.md). The `admin` user is created on first start from
`GITNODE_BOOTSTRAP_ADMIN_PASSWORD` (without it no admin user exists).

## Tests

| Command | Runs |
|---------|------|
| `make test-backend` | Unit tests |
| `make verify` | Full CI gate |
| `./mvnw test -pl gitnode-backend -Dtest=GitNodeArchitectureTest` | Module boundary check |

Flyway migrations: `src/main/resources/db/migration/V0NN__description.sql`
