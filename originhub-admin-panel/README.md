# OriginHub Admin Panel

Platform administrator UI for instance statistics, user management, enterprise organizations, and SAML SSO configuration.

## Requirements

- Node.js >= 24, < 25
- pnpm
- OriginHub backend running (default `http://localhost:8080`)

## Setup

```bash
cd originhub-admin-panel
cp .env.example .env   # optional: set ORIGINHUB_API_URL
pnpm install
pnpm start
```

Dev server runs at **http://localhost:4300** (separate from the main frontend on 4200).

## Scripts

| Command | Description |
|---------|-------------|
| `pnpm start` | Generate `environment.ts` and start dev server on port 4300 |
| `pnpm build` | Production build |
| `pnpm lint` | ESLint |
| `pnpm lint-fix` | ESLint with auto-fix |
| `pnpm format` | Prettier write |

## Environment

`set-env.js` reads `.env` and writes `src/environments/environment.ts`:

| Variable | Default | Description |
|----------|---------|-------------|
| `ORIGINHUB_API_URL` | `http://localhost:8080` | OriginHub API base URL (no trailing slash) |

## Authentication

Sign in with a **platform administrator** account. The panel prefers `POST /api/admin/auth/login`, which validates the `platformAdmin` flag server-side. If that endpoint is unavailable (404/405), it falls back to `POST /api/auth/login` and probes `GET /api/admin/organizations` to confirm platform-admin access.

JWT tokens are stored in `localStorage` under admin-specific keys (`admin_token`, etc.) so they do not conflict with the main OriginHub frontend session.

Non-admin users see a clear error and are redirected to login. HTTP 403 on `/api/admin/**` also triggers logout with an error toast.

## Features

- **Dashboard** — platform overview (users, repos, storage), activity tables with daily/weekly period toggle, top repo contributors, and recent organizations
- **Users** — list accounts, filter by username, enable/disable with confirmation on disable
- **Organizations** — list, create, edit, delete
- **SSO config** — IdP metadata URI, email/username attributes, SP Entity ID override, test connection

## API endpoints

All admin routes require a platform-admin JWT:

**Auth**

- `POST /api/admin/auth/login` (preferred; falls back to `/api/auth/login`)

**Statistics**

- `GET /api/admin/stats/overview`
- `GET /api/admin/stats/repos?period=week|day`
- `GET /api/admin/stats/uploads?period=week|day`

**Users**

- `GET /api/admin/users`
- `PUT /api/admin/users/{id}/enabled`

**Organizations**

- `GET /api/admin/organizations`
- `GET /api/admin/organizations/{slug}`
- `POST /api/admin/organizations`
- `PUT /api/admin/organizations/{slug}`
- `DELETE /api/admin/organizations/{slug}`
- `PUT /api/admin/organizations/{slug}/sso`
- `POST /api/admin/organizations/{slug}/sso/test`

## Testing locally

1. Start the OriginHub backend with the `local` profile (`./mvnw spring-boot:run -pl originhub-backend -Dspring-boot.run.profiles=local`). Bootstrap admin is created on first start when `originhub.bootstrap.admin.password` is set in `application-local.yaml`.
2. Run `pnpm start` in this directory.
3. Open http://localhost:4300/login and sign in with the local bootstrap credentials:

| Field | Value |
|-------|-------|
| Username | `admin` |
| Password | `Admin123` |

The backend must allow CORS from `http://localhost:4300` (included in `application-local.yaml` and default `application.yaml`).
4. **Dashboard** — verify overview cards and activity tables; toggle Daily/Weekly.
5. **Users** — open `/users`, search by username, disable a test user (confirm modal), then re-enable.
6. **Organizations** — create or edit an organization and test SSO configuration.

Run quality checks before committing:

```bash
pnpm lint
pnpm build
```
