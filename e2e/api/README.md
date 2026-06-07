# API E2E

REST endpoint tests. Shared **owner** + **intruder** accounts from [`global-setup.ts`](../global-setup.ts) → `e2e/.auth/session.json`.

## Run

```bash
pnpm test:e2e:api
```

Needs API up (`make infra` + `make dev-backend`, or `make up`).

## Coverage

| Folder | Area |
|--------|------|
| `auth/` | register, login, refresh, password recovery |
| `profile/` | me, profile, password, public profile |
| `repo/`, `branch/`, `commit/`, `tree/` | Git hosting |
| `pr/`, `issue/`, `task/` | PRs, issues, projects |
| `snippet/`, `tag/`, `webhook/` | snippets, releases, webhooks |
| `z-collaborators.spec.ts` | invites & permissions |

User cleanup runs in [teardown](../teardown/README.md) — not part of `test:e2e:api` alone.
