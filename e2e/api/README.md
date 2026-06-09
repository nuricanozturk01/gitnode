# API E2E

REST endpoint tests. Shared owner/intruder from `global-setup.ts`.

## Run

| Command             | Runs           |
| ------------------- | -------------- |
| `pnpm test:e2e:api` | All REST tests |

Needs backend at :8080. User cleanup: [teardown](../teardown/README.md) (not included in api-only run).

## Coverage

`auth/` · `profile/` · `repo/` · `branch/` · `commit/` · `tree/` · `pr/` · `issue/` · `task/` · `snippet/` · `tag/` · `webhook/` · `actions/` · `ssh/` · `migration/` · collaborators

### actions/

| Suite                       | What it tests                                         |
| --------------------------- | ----------------------------------------------------- |
| Runner                      | Registration token (`ghrt_` prefix), runner list      |
| Workflows                   | List, enable, disable, dispatch (`workflow_dispatch`) |
| Runs                        | List (paginated), get detail, cancel, delete          |
| Secrets CRUD                | Create, list (value never returned), update, delete   |
| Public repo — anon read     | GET workflows/runs return 200 without auth            |
| Private repo — anon blocked | GET workflows/runs return 403 without auth            |
