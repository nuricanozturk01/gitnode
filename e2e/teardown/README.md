# Teardown

Deletes test users created in global-setup.

| Command                  | Runs                                                                  |
| ------------------------ | --------------------------------------------------------------------- |
| `pnpm test:e2e:teardown` | Delete owner + intruder (needs prior session in `.auth/session.json`) |

Auto-runs at end of `pnpm test:e2e`. Skipped when using fixed accounts from `.env`.
