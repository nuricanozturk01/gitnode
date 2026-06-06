# API E2E

REST endpoint tests. Uses **owner** + **intruder** from [`global-setup.ts`](../global-setup.ts) → `e2e/.auth/session.json`.

## Run

```bash
cd e2e
pnpm test:e2e:api
```

Needs API up (`make infra` + backend, or `make up`).

## What’s covered

| Folder | Area |
| --- | --- |
| `auth/` | register, login, refresh, password recovery |
| `profile/` | me, profile, password, public profile |
| `repo/`, `branch/`, `commit/`, `tree/` | Git hosting API |
| `pr/`, `issue/`, `task/` | PRs, issues, projects |
| `snippet/`, `tag/`, `webhook/` | snippets, releases, webhooks |
| `z-collaborators.spec.ts` | invites & permissions |
| `shared/` | health check |

User cleanup after a full run: [teardown](../teardown/README.md) — not part of `test:e2e:api` alone.
