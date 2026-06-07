# Scenario E2E

API + real `git clone/push` flows. Tests run **in parallel** — each spec creates its own repo.

## Run

```bash
pnpm test:e2e:scenario          # scenario + teardown
pnpm test:e2e:scenario:only     # scenario only (CI style)
```

Needs **git** on PATH and running backend.

## SAML & LDAP (optional, not in CI)

| | LDAP | SAML |
|---|------|------|
| Command | `pnpm test:e2e:ldap` | `pnpm test:e2e:saml` |
| Extra setup | `make ldap-up` | `make saml-keygen` |
| Admin login | `admin` / `Admin123` | same |

**LDAP:** `make ldap-up` maps port 389→10389. Test user: `fry` / `fry` @ `planetexpress.com`.

**SAML:** needs outbound HTTPS to [samltest.dev](https://www.samltest.dev). API-only — no browser IdP login.

Each run: create temp org → test → delete org (`afterAll`).

## Known quirks

- SCN-PRIV/PUB-GIT-04 skipped — HTTP Git write ACL looser than SSH
- SAML/LDAP always skipped in CI

Spec files use `SCN-*` IDs in filenames and test titles.
