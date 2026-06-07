# OriginHub Frontend

Main Angular SPA — repos, PRs, issues, Actions.

Part of **base app**. Run profile: [CONTRIBUTING.md](../CONTRIBUTING.md#base-app--frontend--backend)

## Run

```bash
make dev-backend                  # terminal 1 — backend :8080
cd originhub-frontend && pnpm start   # terminal 2 → :4200
```

First time: `make dev-setup` from repo root.

## Scripts

| Command | Runs |
|---------|------|
| `pnpm start` | Dev server :4200 |
| `pnpm build` | Production build |
| `pnpm lint` | ESLint |
| `pnpm test` | Karma unit tests |

`set-env.js` writes `environment.ts` from `.env` (optional — see `.env.example`).
