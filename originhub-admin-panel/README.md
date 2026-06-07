# OriginHub Admin Panel

Platform admin UI — stats, users, orgs, SSO.

**Optional.** Needs backend admin API on. Run profile: [CONTRIBUTING.md](../CONTRIBUTING.md#full-app--grafana--admin)

## Run

```bash
# 1. Enable admin API in application-local.yaml → originhub.admin.enabled: true
make dev-backend                        # terminal 1
cd originhub-admin-panel && pnpm start  # terminal 2 → :4300
```

Login: `admin` / `Admin123`

## Scripts

| Command      | Runs             |
|--------------|------------------|
| `pnpm start` | Dev server :4300 |
| `pnpm build` | Production build |
| `pnpm lint`  | ESLint           |

Backend flag (on by default): disable with `ORIGINHUB_ADMIN_ENABLED=false` or `originhub.admin.enabled: false`
