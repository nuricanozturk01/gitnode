# OriginHub E2E

Playwright tests for the OriginHub API and scenario flows. See also:

- [API tests](api/README.md) — REST coverage, auth, modules
- [Scenario tests](scenario/README.md) — SCN-\* catalog, HTTP Git, expected outcomes
- [Teardown](teardown/README.md) — user cleanup after a full run

## Prerequisites

- **Node.js 24** (`engines` in `package.json`)
- **API** reachable (default `http://localhost:8080`), e.g. `make up` or Spring Boot with `local` profile
- **Git** on `PATH` for scenario tests only

```bash
cd e2e
pnpm install
cp .env.example .env   # optional — local credentials and API URL
```

## How to run

| Command                       | What runs                                                             |
| ----------------------------- | --------------------------------------------------------------------- |
| `pnpm test:e2e`               | **API** → **scenario** → **teardown** (full suite)                    |
| `pnpm test:e2e:api`           | API project only (teardown does **not** run)                          |
| `pnpm test:e2e:scenario`      | Scenario + teardown (`E2E_SCENARIO_ONLY=1`, skips API project)        |
| `pnpm test:e2e:scenario:only` | Scenario only (used in CI before the teardown job)                    |
| `pnpm test:e2e:teardown`      | Teardown only (`E2E_TEARDOWN_ONLY=1`, needs `e2e/.auth/session.json`) |

## Environment (`.env`)

Optional file: `e2e/.env` (see [.env.example](.env.example)).

| Variable                                          | When unset                          | When set                              |
| ------------------------------------------------- | ----------------------------------- | ------------------------------------- |
| `ORIGINHUB_API_BASE_URL`                          | `http://localhost:8080`             | Used for all requests                 |
| `E2E_OWNER_USERNAME` + `E2E_OWNER_PASSWORD`       | Owner is **auto-registered**        | Owner logs in via `/api/auth/login`   |
| `E2E_INTRUDER_USERNAME` + `E2E_INTRUDER_PASSWORD` | Intruder is **auto-registered**     | Intruder logs in                      |
| `E2E_PRESERVE_USERS`                              | `1` if any account came from `.env` | Teardown skips `DELETE /api/users/me` |

CI does not use `.env`; it auto-registers users and deletes them in the teardown
job ([workflow](../.github/workflows/originhub-e2e.yaml)).
