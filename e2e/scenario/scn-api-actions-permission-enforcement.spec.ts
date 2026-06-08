/**
 * SCN-ACTIONS-PERM — Actions permission enforcement.
 *
 * Each section grants the shared intruder user a specific permission set,
 * exercises the protected Actions endpoint, then verifies correct allow/deny
 * behaviour. Each test uses the shared privateRepo/publicRepo fixtures.
 *
 * Covers:
 *  SCN-ACTIONS-PERM-WRITE       — dispatch/enable/disable/cancel/delete (403 without ACTIONS_WRITE, 2xx with)
 *  SCN-ACTIONS-SECRETS-OWNER    — secrets list/create/delete owner-only (403 for collab with ACTIONS_WRITE)
 *  SCN-ACTIONS-ADMIN-IMPLIES    — ADMIN permission implies ACTIONS_WRITE (dispatch succeeds)
 *  SCN-ACTIONS-PUBLIC-READ      — unauthenticated GET workflows/runs on public repo → 200
 *  SCN-ACTIONS-PUBLIC-WRITE     — unauthenticated dispatch on public repo → 401/403
 */
import type { APIRequestContext } from '@playwright/test';

import { expect, test } from './fixtures/scenario';
import { getApiBaseUrl } from './helpers/env';
import { putBlob } from './helpers/scenario-api';

// ─── URL helpers ──────────────────────────────────────────────────────────────

function actionsBase(owner: string, repo: string): string {
  return `/api/repos/${owner}/${repo}/actions`;
}
function workflowsApi(owner: string, repo: string): string {
  return `${actionsBase(owner, repo)}/workflows`;
}
function workflowEnableApi(owner: string, repo: string): string {
  return `${workflowsApi(owner, repo)}/enable`;
}
function workflowDisableApi(owner: string, repo: string): string {
  return `${workflowsApi(owner, repo)}/disable`;
}
function workflowDispatchApi(owner: string, repo: string): string {
  return `${actionsBase(owner, repo)}/workflows/dispatches`;
}
function runsApi(owner: string, repo: string): string {
  return `${actionsBase(owner, repo)}/runs`;
}
function runApi(owner: string, repo: string, runId: string): string {
  return `${runsApi(owner, repo)}/${runId}`;
}
function secretsApi(owner: string, repo: string): string {
  return `${actionsBase(owner, repo)}/secrets`;
}
function secretApi(owner: string, repo: string, name: string): string {
  return `${secretsApi(owner, repo)}/${name}`;
}
function collaboratorsApi(owner: string, repo: string): string {
  return `/api/repos/${owner}/${repo}/collaborators`;
}

// ─── Shared collaborator helpers ──────────────────────────────────────────────

async function inviteAndAccept(
  bareApi: APIRequestContext,
  ownerAuth: string,
  owner: string,
  repo: string,
  username: string,
  permissions: string[],
  collabAuth: string,
): Promise<void> {
  const inviteResp = await bareApi.post(collaboratorsApi(owner, repo), {
    headers: { Authorization: ownerAuth },
    data: { username, permissions },
  });
  if (!inviteResp.ok()) {
    throw new Error(`invite failed (${inviteResp.status()}): ${await inviteResp.text()}`);
  }
  await bareApi.post(`${collaboratorsApi(owner, repo)}/invitation/accept`, {
    headers: { Authorization: collabAuth },
  });
}

async function setPermissions(
  bareApi: APIRequestContext,
  ownerAuth: string,
  owner: string,
  repo: string,
  username: string,
  permissions: string[],
): Promise<void> {
  const resp = await bareApi.put(`${collaboratorsApi(owner, repo)}/${username}/permissions`, {
    headers: { Authorization: ownerAuth },
    data: { permissions },
  });
  if (!resp.ok()) {
    throw new Error(`set perms failed (${resp.status()}): ${await resp.text()}`);
  }
}

async function removeCollaborator(
  bareApi: APIRequestContext,
  ownerAuth: string,
  owner: string,
  repo: string,
  username: string,
): Promise<void> {
  await bareApi
    .delete(`${collaboratorsApi(owner, repo)}/${username}`, {
      headers: { Authorization: ownerAuth },
    })
    .catch(() => {});
}

// ─── Workflow YAML helpers ─────────────────────────────────────────────────────

const DISPATCH_WORKFLOW_PATH = '.originhub/workflows/scn-dispatch.yaml';
const DISPATCH_WORKFLOW_YAML = `name: SCN Dispatch Test
on:
  workflow_dispatch:
    inputs:
      message:
        description: Test message
        required: false
jobs:
  test:
    runs-on: [self-hosted]
    steps:
      - name: Echo
        run: echo hello
`;

async function createDispatchWorkflowInRepo(
  bareApi: APIRequestContext,
  authorization: string,
  owner: string,
  repo: string,
): Promise<void> {
  await putBlob(
    bareApi,
    authorization,
    owner,
    repo,
    'main',
    DISPATCH_WORKFLOW_PATH,
    DISPATCH_WORKFLOW_YAML,
    'e2e: add dispatch workflow',
  );
}

// ─── SCN-ACTIONS-PERM-WRITE ───────────────────────────────────────────────────

test.describe('SCN-ACTIONS-PERM-WRITE — ACTIONS_WRITE enforcement', () => {
  test('collab without ACTIONS_WRITE cannot dispatch; with ACTIONS_WRITE can dispatch', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }) => {
    try {
      await createDispatchWorkflowInRepo(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
      );

      await inviteAndAccept(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        intruder.username,
        ['READ'],
        intruder.authorization,
      );

      // Without ACTIONS_WRITE → 403
      const denyResp = await bareApi.post(workflowDispatchApi(owner.username, privateRepo.name), {
        headers: { Authorization: intruder.authorization },
        data: { filePath: DISPATCH_WORKFLOW_PATH, ref: 'main' },
      });
      expect(denyResp.status()).toBe(403);

      await setPermissions(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        intruder.username,
        ['READ', 'ACTIONS_WRITE'],
      );

      // With ACTIONS_WRITE → 204
      const allowResp = await bareApi.post(workflowDispatchApi(owner.username, privateRepo.name), {
        headers: { Authorization: intruder.authorization },
        data: { filePath: DISPATCH_WORKFLOW_PATH, ref: 'main' },
      });
      expect(allowResp.status()).toBe(204);
    } finally {
      await removeCollaborator(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        intruder.username,
      );
    }
  });

  test('collab without ACTIONS_WRITE cannot enable workflow; with ACTIONS_WRITE can', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }) => {
    try {
      await createDispatchWorkflowInRepo(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
      );

      await inviteAndAccept(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        intruder.username,
        ['READ'],
        intruder.authorization,
      );

      // Without ACTIONS_WRITE → 403 on enable
      const denyEnable = await bareApi.put(workflowEnableApi(owner.username, privateRepo.name), {
        headers: { Authorization: intruder.authorization },
        params: { filePath: DISPATCH_WORKFLOW_PATH },
      });
      expect(denyEnable.status()).toBe(403);

      // Without ACTIONS_WRITE → 403 on disable
      const denyDisable = await bareApi.put(workflowDisableApi(owner.username, privateRepo.name), {
        headers: { Authorization: intruder.authorization },
        params: { filePath: DISPATCH_WORKFLOW_PATH },
      });
      expect(denyDisable.status()).toBe(403);

      await setPermissions(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        intruder.username,
        ['READ', 'ACTIONS_WRITE'],
      );

      // With ACTIONS_WRITE → 204 on disable
      const allowDisable = await bareApi.put(workflowDisableApi(owner.username, privateRepo.name), {
        headers: { Authorization: intruder.authorization },
        params: { filePath: DISPATCH_WORKFLOW_PATH },
      });
      expect(allowDisable.status()).toBe(204);

      // With ACTIONS_WRITE → 204 on re-enable
      const allowEnable = await bareApi.put(workflowEnableApi(owner.username, privateRepo.name), {
        headers: { Authorization: intruder.authorization },
        params: { filePath: DISPATCH_WORKFLOW_PATH },
      });
      expect(allowEnable.status()).toBe(204);
    } finally {
      await removeCollaborator(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        intruder.username,
      );
    }
  });

  test('collab without ACTIONS_WRITE cannot cancel/delete run; with ACTIONS_WRITE can', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }) => {
    try {
      // Create workflow and dispatch as owner to get a run
      await createDispatchWorkflowInRepo(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
      );
      const dispatchResp = await bareApi.post(
        workflowDispatchApi(owner.username, privateRepo.name),
        {
          headers: { Authorization: owner.authorization },
          data: { filePath: DISPATCH_WORKFLOW_PATH, ref: 'main' },
        },
      );
      expect(dispatchResp.status()).toBe(204);

      const runsResp = await bareApi.get(runsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
      });
      expect(runsResp.status()).toBe(200);
      const runsBody = (await runsResp.json()) as { content: { id: string }[] };
      expect(runsBody.content.length).toBeGreaterThan(0);
      const runId = runsBody.content[0].id;

      await inviteAndAccept(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        intruder.username,
        ['READ'],
        intruder.authorization,
      );

      // Without ACTIONS_WRITE → 403 on cancel
      const denyCancel = await bareApi.post(
        `${runApi(owner.username, privateRepo.name, runId)}/cancel`,
        { headers: { Authorization: intruder.authorization } },
      );
      expect(denyCancel.status()).toBe(403);

      // Without ACTIONS_WRITE → 403 on delete
      const denyDelete = await bareApi.delete(runApi(owner.username, privateRepo.name, runId), {
        headers: { Authorization: intruder.authorization },
      });
      expect(denyDelete.status()).toBe(403);

      await setPermissions(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        intruder.username,
        ['READ', 'ACTIONS_WRITE'],
      );

      // With ACTIONS_WRITE → 204 on cancel (may be already finished but endpoint still 204)
      const allowCancel = await bareApi.post(
        `${runApi(owner.username, privateRepo.name, runId)}/cancel`,
        { headers: { Authorization: intruder.authorization } },
      );
      expect([204, 409]).toContain(allowCancel.status());

      // With ACTIONS_WRITE → 204 on delete
      const allowDelete = await bareApi.delete(runApi(owner.username, privateRepo.name, runId), {
        headers: { Authorization: intruder.authorization },
      });
      expect(allowDelete.status()).toBe(204);
    } finally {
      await removeCollaborator(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        intruder.username,
      );
    }
  });
});

// ─── SCN-ACTIONS-SECRETS-OWNER ────────────────────────────────────────────────

test.describe('SCN-ACTIONS-SECRETS-OWNER — secrets are owner-only', () => {
  test('collab with ACTIONS_WRITE cannot list, create, or delete secrets', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }) => {
    const SECRET_NAME = 'SCN_ACTIONS_SECRET';
    try {
      // Owner creates a secret
      const createResp = await bareApi.put(
        secretApi(owner.username, privateRepo.name, SECRET_NAME),
        {
          headers: { Authorization: owner.authorization },
          data: { value: 'owner-secret-value' },
        },
      );
      expect(createResp.status()).toBe(204);

      await inviteAndAccept(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        intruder.username,
        ['READ', 'ACTIONS_WRITE'],
        intruder.authorization,
      );

      // Collab with ACTIONS_WRITE cannot list secrets
      const listResp = await bareApi.get(secretsApi(owner.username, privateRepo.name), {
        headers: { Authorization: intruder.authorization },
      });
      expect(listResp.status()).toBe(403);

      // Collab with ACTIONS_WRITE cannot create secret
      const createByCollabResp = await bareApi.put(
        secretApi(owner.username, privateRepo.name, 'SCN_COLLAB_SECRET'),
        {
          headers: { Authorization: intruder.authorization },
          data: { value: 'collab-value' },
        },
      );
      expect(createByCollabResp.status()).toBe(403);

      // Collab with ACTIONS_WRITE cannot delete secret
      const deleteResp = await bareApi.delete(
        secretApi(owner.username, privateRepo.name, SECRET_NAME),
        { headers: { Authorization: intruder.authorization } },
      );
      expect(deleteResp.status()).toBe(403);

      // Owner can still list secrets
      const ownerListResp = await bareApi.get(secretsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
      });
      expect(ownerListResp.status()).toBe(200);
      const secrets = (await ownerListResp.json()) as { name: string }[];
      expect(secrets.some((s) => s.name === SECRET_NAME)).toBe(true);
      // Value must never appear in list
      const s = secrets.find((x) => x.name === SECRET_NAME)!;
      expect(Object.keys(s)).not.toContain('value');
    } finally {
      await bareApi
        .delete(secretApi(owner.username, privateRepo.name, SECRET_NAME), {
          headers: { Authorization: owner.authorization },
        })
        .catch(() => {});
      await removeCollaborator(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        intruder.username,
      );
    }
  });
});

// ─── SCN-ACTIONS-ADMIN-IMPLIES ────────────────────────────────────────────────

test.describe('SCN-ACTIONS-ADMIN-IMPLIES — ADMIN implies ACTIONS_WRITE', () => {
  test('collab with ADMIN can dispatch workflow', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }) => {
    try {
      await createDispatchWorkflowInRepo(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
      );

      await inviteAndAccept(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        intruder.username,
        ['ADMIN'],
        intruder.authorization,
      );

      // ADMIN implies ACTIONS_WRITE → dispatch succeeds
      const dispatchResp = await bareApi.post(
        workflowDispatchApi(owner.username, privateRepo.name),
        {
          headers: { Authorization: intruder.authorization },
          data: { filePath: DISPATCH_WORKFLOW_PATH, ref: 'main' },
        },
      );
      expect(dispatchResp.status()).toBe(204);

      // ADMIN implies ACTIONS_WRITE → enable/disable succeed
      const disableResp = await bareApi.put(workflowDisableApi(owner.username, privateRepo.name), {
        headers: { Authorization: intruder.authorization },
        params: { filePath: DISPATCH_WORKFLOW_PATH },
      });
      expect(disableResp.status()).toBe(204);
    } finally {
      await removeCollaborator(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        intruder.username,
      );
    }
  });
});

// ─── SCN-ACTIONS-PUBLIC-READ ──────────────────────────────────────────────────

test.describe('SCN-ACTIONS-PUBLIC-READ — unauthenticated read on public repo', () => {
  let anonRequest: APIRequestContext;

  test.beforeAll(async ({ playwright, bareApi, owner, publicRepo }) => {
    // Add workflow to public repo so there is something to read
    await createDispatchWorkflowInRepo(
      bareApi,
      owner.authorization,
      owner.username,
      publicRepo.name,
    );

    anonRequest = await playwright.request.newContext({
      baseURL: getApiBaseUrl(),
      extraHTTPHeaders: { Accept: 'application/json' },
    });
  });

  test.afterAll(async () => {
    await anonRequest?.dispose();
  });

  test('GET workflows — 200 without auth on public repo', async ({ owner, publicRepo }) => {
    const resp = await anonRequest.get(workflowsApi(owner.username, publicRepo.name));
    expect(resp.status()).toBe(200);
    expect(Array.isArray(await resp.json())).toBe(true);
  });

  test('GET runs — 200 without auth on public repo', async ({ owner, publicRepo }) => {
    const resp = await anonRequest.get(runsApi(owner.username, publicRepo.name));
    expect(resp.status()).toBe(200);
    const body = (await resp.json()) as { content: unknown[] };
    expect(Array.isArray(body.content)).toBe(true);
  });
});

// ─── SCN-ACTIONS-PUBLIC-WRITE ─────────────────────────────────────────────────

test.describe('SCN-ACTIONS-PUBLIC-WRITE — unauthenticated write blocked on public repo', () => {
  let anonRequest: APIRequestContext;

  test.beforeAll(async ({ playwright, bareApi, owner, publicRepo }) => {
    await createDispatchWorkflowInRepo(
      bareApi,
      owner.authorization,
      owner.username,
      publicRepo.name,
    );

    anonRequest = await playwright.request.newContext({
      baseURL: getApiBaseUrl(),
      extraHTTPHeaders: { Accept: 'application/json' },
    });
  });

  test.afterAll(async () => {
    await anonRequest?.dispose();
  });

  test('POST dispatch — 401/403 without auth on public repo', async ({ owner, publicRepo }) => {
    const resp = await anonRequest.post(workflowDispatchApi(owner.username, publicRepo.name), {
      data: { filePath: DISPATCH_WORKFLOW_PATH, ref: 'main' },
    });
    expect([401, 403]).toContain(resp.status());
  });

  test('PUT disable workflow — 401/403 without auth on public repo', async ({
    owner,
    publicRepo,
  }) => {
    const resp = await anonRequest.put(workflowDisableApi(owner.username, publicRepo.name), {
      params: { filePath: DISPATCH_WORKFLOW_PATH },
    });
    expect([401, 403]).toContain(resp.status());
  });
});
