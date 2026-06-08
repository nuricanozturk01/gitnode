# GitNode Admin Panel

Platform admin UI — stats, users, orgs, SSO.

**Optional.** Needs backend admin API on. Run profile: [CONTRIBUTING.md](../CONTRIBUTING.md#full-app--grafana--admin)

## Run

```bash
# 1. Enable admin API in application-local.yaml → gitnode.admin.enabled: true
make dev-backend                        # terminal 1
cd gitnode-admin-panel && pnpm start  # terminal 2 → :4300
```

Login: `admin` / `Admin123`

## Scripts

| Command      | Runs             |
|--------------|------------------|
| `pnpm start` | Dev server :4300 |
| `pnpm build` | Production build |
| `pnpm lint`  | ESLint           |

Backend flag (on by default): disable with `GITNODE_ADMIN_ENABLED=false` or `gitnode.admin.enabled: false`
