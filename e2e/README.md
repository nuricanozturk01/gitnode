# OriginHub E2E

Playwright tests against the OriginHub API. No browser — HTTP only.

## Quick start (local)

From repo root:

```bash
make infra                                                          # Postgres + Redis
./mvnw spring-boot:run -pl originhub-backend -Dspring-boot.run.profiles=local

cd e2e && pnpm install && pnpm test:e2e                           # full suite
```

Default API: `http://localhost:8080` · Node **24** · needs **git** for scenario tests.

## Commands

| Command                  | What                       |
| ------------------------ | -------------------------- |
| `pnpm test:e2e`          | API → scenario → teardown  |
| `pnpm test:e2e:api`      | REST tests only            |
| `pnpm test:e2e:scenario` | Scenario + teardown        |
| `pnpm test:e2e:saml`     | SAML SSO (optional, local) |
| `pnpm test:e2e:ldap`     | LDAP SSO (optional, local) |

Optional `.env`: copy [.env.example](.env.example) to `.env`.

## SAML & LDAP (optional, not in CI)

Both are **skipped by default**. Run locally when you work on enterprise SSO.

**LDAP**

```bash
make infra
./mvnw spring-boot:run -pl originhub-backend -Dspring-boot.run.profiles=local
make ldap-up                    # test OpenLDAP on localhost:389
cd e2e && pnpm test:e2e:ldap
make ldap-down                  # when done
```

**SAML**

```bash
make saml-keygen                # once — ~/.originhub/saml/
make infra
./mvnw spring-boot:run -pl originhub-backend -Dspring-boot.run.profiles=local
cd e2e && pnpm test:e2e:saml    # needs internet → samltest.dev
```

Admin login for both: `admin` / `Admin123` (local profile).

## Docs

- [API tests](api/README.md)
- [Scenario tests](scenario/README.md)
- [Teardown](teardown/README.md)

## CI

GitHub Actions runs API + scenario against production. **SAML and LDAP are not run in CI** (no env flags, no LDAP container).
