/**
 * SSH endpoint used by the scenarios that exercise Git over SSH.
 *
 * Locally the backend runs with `GITNODE_SSH_PORT=12222` on 127.0.0.1. The scheduled CI run targets
 * the deployed API, which exposes no SSH endpoint to the runner, so the workflow sets
 * `GITNODE_E2E_SSH=false` and the SSH-only steps are skipped there (API and HTTP checks still run).
 */
export const SSH_HOST = process.env.GITNODE_SSH_HOST ?? '127.0.0.1';
export const SSH_PORT = process.env.GITNODE_SSH_PORT ?? '12222';
export const SSH_ENABLED = process.env.GITNODE_E2E_SSH !== 'false';
