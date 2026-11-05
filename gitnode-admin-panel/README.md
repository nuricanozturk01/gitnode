# GitNode Admin Panel

Platform admin UI — stats, users, orgs, SSO.

**Optional.** Needs the backend running (its admin API is always on). Run profile: [CONTRIBUTING.md](../CONTRIBUTING.md#full-app--admin)

## Run

```bash
make dev-setup                          # once (creates .env.local, installs dependencies)
make dev-backend                        # terminal 1
cd gitnode-admin-panel && pnpm start    # terminal 2 → :4300
```

Login: `admin` / `Admin123` (the password in `.env.local`, `GITNODE_BOOTSTRAP_ADMIN_PASSWORD`)

## Scripts

| Command      | Runs             |
|--------------|------------------|
| `pnpm start` | Dev server :4300 |
| `pnpm build` | Production build |
| `pnpm lint`  | ESLint           |
