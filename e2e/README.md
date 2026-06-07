# OriginHub E2E

Playwright HTTP tests. No browser.

Requires running backend. See [CONTRIBUTING.md](../CONTRIBUTING.md#tests)

## Run

```bash
make dev-backend              # terminal 1
cd e2e && pnpm test:e2e       # API → scenario → teardown
```

Needs **git** on PATH for scenario tests.

## Commands

| Command | Runs |
|---------|------|
| `pnpm test:e2e` | Full suite |
| `pnpm test:e2e:api` | REST tests only |
| `pnpm test:e2e:scenario` | Git clone/push flows |
| `pnpm test:e2e:saml` | SAML SSO (local, optional) |
| `pnpm test:e2e:ldap` | LDAP SSO (local, optional) |

## Docs

- [api/](api/README.md) · [scenario/](scenario/README.md) · [teardown/](teardown/README.md)
