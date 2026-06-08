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

| Command                  | Runs                                    |
| ------------------------ | --------------------------------------- |
| `pnpm test:e2e`          | Full suite (api → scenario → teardown)  |
| `pnpm test:e2e:api`      | REST tests only                         |
| `pnpm test:e2e:scenario` | Scenario + permission enforcement tests |
| `pnpm test:e2e:teardown` | Teardown only                           |

SAML and LDAP tests are **not part of the default workflow**. Run them explicitly:

| Command              | Requires                                                                                               |
| -------------------- | ------------------------------------------------------------------------------------------------------ |
| `pnpm test:e2e:saml` | Backend with SAML enabled + SP signing key, see [scenario/README.md](scenario/README.md#optional-saml) |
| `pnpm test:e2e:ldap` | `make ldap-up` + backend with LDAP enabled, see [scenario/README.md](scenario/README.md#optional-ldap) |

## Coverage

| Area                                        | API tests | Scenario tests |
| ------------------------------------------- | --------- | -------------- |
| Auth (login, refresh, SSH keys)             | ✓         | —              |
| Repository CRUD                             | ✓         | ✓              |
| Branch operations                           | ✓         | ✓              |
| Commits / tree / blob                       | ✓         | —              |
| Pull requests                               | ✓         | ✓              |
| Issues                                      | ✓         | ✓              |
| Tasks / Kanban                              | ✓         | ✓              |
| Snippets                                    | ✓         | —              |
| Tags / releases                             | ✓         | —              |
| Webhooks                                    | ✓         | ✓              |
| Actions (runners, workflows, runs, secrets) | ✓         | ✓              |
| Collaborator access & permissions           | ✓         | ✓              |
| Migration (GitHub import)                   | ✓         | —              |
| SSH key auth                                | ✓         | —              |
| Git HTTP (clone/push)                       | —         | ✓              |
| SAML SSO                                    | —         | ✓ _(optional)_ |
| LDAP SSO                                    | —         | ✓ _(optional)_ |

## Docs

- [api/](api/README.md) · [scenario/](scenario/README.md) · [teardown/](teardown/README.md)
