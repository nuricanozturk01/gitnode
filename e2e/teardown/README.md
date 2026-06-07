# Teardown

Deletes **owner** and **intruder** users from global-setup. Runs automatically at end of `pnpm test:e2e`.

## Run alone

```bash
pnpm test:e2e:teardown    # needs e2e/.auth/session.json from prior run
```

Skipped when accounts come from `.env` (`E2E_PRESERVE_USERS=1`). Also removes temp git workdirs under `scenario/.workdirs`.
