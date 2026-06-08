# GitNode Backend

Spring Boot API + Git HTTP/SSH. Production image embeds the built frontend.

Part of **base app**. Run profile: [CONTRIBUTING.md](../CONTRIBUTING.md#base-app--frontend--backend)

## Run

```bash
make dev-setup      # once
make dev-backend    # → http://localhost:8080, SSH :2222
```

Or manually:

```bash
make infra
cp src/main/resources/application-local.yaml.example \
   src/main/resources/application-local.yaml
./mvnw spring-boot:run -pl gitnode-backend -Dspring-boot.run.profiles=local
```

## Admin API

On by default (`application-local.yaml` → `gitnode.admin.enabled: true`). Disable:

```yaml
gitnode:
  admin:
    enabled: false
```

Required for [admin panel](../gitnode-admin-panel/README.md).

## Tests

| Command | Runs |
|---------|------|
| `make test-backend` | Unit tests |
| `make verify` | Full CI gate |
| `./mvnw test -Dtest=GitNodeArchitectureTest` | Module boundary check |

Flyway migrations: `src/main/resources/db/migration/V0NN__description.sql`
