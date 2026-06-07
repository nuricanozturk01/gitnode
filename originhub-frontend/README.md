# OriginHub Frontend

Main Angular 21 SPA — repo browsing, PRs, issues, Actions, settings.

## Requirements

Node.js **24** · pnpm · Backend at http://localhost:8080

## Quick start

```bash
make dev-setup          # from repo root (once)
cp .env.example .env    # optional

pnpm start              # http://localhost:4200
```

`set-env.js` runs before start/build and writes `src/environments/environment.ts` from `.env`.

## Scripts

| Command                       | Description         |
|-------------------------------|---------------------|
| `pnpm start`                  | Dev server on :4200 |
| `pnpm build`                  | Production build    |
| `pnpm lint` / `pnpm lint-fix` | ESLint              |
| `pnpm test`                   | Karma unit tests    |
| `pnpm format`                 | Prettier            |
