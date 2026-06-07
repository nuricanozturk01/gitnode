# OriginHub E2E

Playwright HTTP tests against the OriginHub API. No browser.

## Quick start

```bash
make dev-setup                              # from repo root (once)
make dev-backend                            # terminal 1

cd e2e && pnpm test:e2e                     # API → scenario → teardown
```

Needs **git** on PATH for scenario tests. Default API: `http://localhost:8080`.

Optional: `cp .env.example .env`

## Commands

| Command | What |
|---------|------|
| `pnpm test:e2e` | Full suite (api → scenario → teardown) |
| `pnpm test:e2e:api` | REST tests only |
| `pnpm test:e2e:scenario` | Git clone/push flows + teardown |
| `pnpm test:e2e:saml` | SAML SSO *(local, optional)* |
| `pnpm test:e2e:ldap` | LDAP SSO *(local, optional)* |

SAML/LDAP skipped by default. Need `make saml-keygen` or `make ldap-up` — see [scenario/README.md](scenario/README.md).

## Layout

| Folder | Purpose |
|--------|---------|
| `api/` | REST endpoint tests |
| `scenario/` | End-to-end flows with real git ops |
| `teardown/` | Cleanup test users after full run |
| `global-setup.ts` | Creates owner + intruder test accounts |

## Docs

- [API tests](api/README.md)
- [Scenario tests](scenario/README.md)
- [Teardown](teardown/README.md)

See [CONTRIBUTING.md](../CONTRIBUTING.md) for full dev guide.
