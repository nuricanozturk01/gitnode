# E2E teardown

Playwright project `teardown` runs **after** scenario (or alone in CI’s third job).

## What it does

[`delete-e2e-users.spec.ts`](delete-e2e-users.spec.ts):

1. `DELETE /api/users/me` as **intruder** → expect **204**
2. `DELETE /api/users/me` as **owner** → expect **204**
3. Remove `e2e/.auth/session.json`
4. Remove `scenario/.workdirs` and `.git-workdirs` (always, including when user delete is skipped)

Skipped when `session.preserveUsers` is true (accounts from `.env`); workdir cleanup still runs.

## Run

```bash
pnpm test:e2e:teardown
```

Needs `e2e/.auth/session.json` from a prior run. With `E2E_TEARDOWN_ONLY=1`, `global-setup` does not re-register users.
