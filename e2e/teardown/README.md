# Teardown

Deletes the **owner** and **intruder** users created in global-setup. Runs automatically at the end of `pnpm test:e2e`.

## Run alone

```bash
cd e2e
pnpm test:e2e:teardown    # needs e2e/.auth/session.json from a prior run
```

Skipped when accounts come from `.env` (`E2E_PRESERVE_USERS=1`).

Also removes temp git workdirs under `scenario/.workdirs`.
