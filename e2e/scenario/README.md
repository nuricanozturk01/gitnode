# Scenario E2E

Real `git clone/push` flows. Parallel — each spec creates its own repo.

## Run

| Command | Runs |
|---------|------|
| `pnpm test:e2e:scenario` | Scenario + teardown |
| `pnpm test:e2e:scenario:only` | Scenario only (CI style) |
| `pnpm test:e2e:saml` | SAML SSO (local, needs admin API) |
| `pnpm test:e2e:ldap` | LDAP SSO (local, needs `make ldap-up`) |

Needs backend + **git** on PATH. Admin login: `admin` / `Admin123`

SAML/LDAP skipped in CI.
