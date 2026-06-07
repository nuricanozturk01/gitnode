# API E2E

REST endpoint tests. Shared owner/intruder from `global-setup.ts`.

## Run

| Command | Runs |
|---------|------|
| `pnpm test:e2e:api` | All REST tests |

Needs backend at :8080. User cleanup: [teardown](../teardown/README.md) (not included in api-only run).

## Coverage

`auth/` · `profile/` · `repo/` · `branch/` · `commit/` · `tree/` · `pr/` · `issue/` · `task/` · `snippet/` · `tag/` · `webhook/` · collaborators
