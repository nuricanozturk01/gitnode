# OriginHub Admin Panel

Platform administrator UI — stats, users, organizations, SAML/LDAP SSO.

**Optional.** Separate Angular app — not bundled with main frontend. Run only when you need platform admin features.

## Requirements

- Node.js **24** · pnpm
- Backend with **admin API enabled** (`ORIGINHUB_ADMIN_ENABLED=true`)

## Quick start

```bash
# 1. Enable admin API on backend (application-local.yaml or env)
#    originhub.admin.enabled: true

make dev-setup && make dev-backend     # terminal 1

# 2. Start admin panel
cp .env.example .env                   # optional
pnpm start                             # terminal 2 → http://localhost:4300
```

Login: bootstrap admin **admin** / **Admin123** (local profile).

## Backend flag

Admin panel needs backend admin module active:

| Property                  | Env var                   | Default |
|---------------------------|---------------------------|---------|
| `originhub.admin.enabled` | `ORIGINHUB_ADMIN_ENABLED` | `false` |

When `false`, `/api/admin/**` endpoints and all admin module beans are **not loaded** at runtime.

```yaml
# application-local.yaml
originhub:
  admin:
    enabled: true
```

Or Docker: `ADMIN_ENABLED=true make app`

## How it works

- Separate app on port **4300** (main UI is :4200)
- Calls `POST /api/admin/auth/login` → JWT stored in admin-specific localStorage keys
- `set-env.js` writes `environment.ts` from `ORIGINHUB_API_URL` (default `http://localhost:8080`)

## Scripts

| Command      | Description      |
|--------------|------------------|
| `pnpm start` | Dev server :4300 |
| `pnpm build` | Production build |
| `pnpm lint`  | ESLint           |
