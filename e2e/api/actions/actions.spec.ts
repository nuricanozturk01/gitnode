/**
 * Actions API tests.
 *
 * Covers:
 *  - Runner registration token
 *  - Runner list
 *  - Workflow list / enable / disable
 *  - Workflow dispatch (workflow_dispatch trigger)
 *  - Run list / get / cancel / delete
 *  - Secrets CRUD (owner)
 *  - Public-repo read without auth (GET endpoints)
 *  - Private-repo read blocked without auth (GET endpoints)
 */
import type { APIRequestContext } from '@playwright/test';

import { expect, test } from '../fixtures/authenticated-api';
import { getApiBaseUrl } from '../helpers/env';

const actionsBase = (owner: string, repo: string) => `/api/repos/${owner}/${repo}/actions`;
const runnersApi = () => `/api/actions/runners`;
const registrationTokenApi = () => `${runnersApi()}/registration-token`;
const runsApi = (owner: string, repo: string) => `${actionsBase(owner, repo)}/runs`;
const runApi = (owner: string, repo: string, runId: string) => `${runsApi(owner, repo)}/${runId}`;
const workflowsApi = (owner: string, repo: string) => `${actionsBase(owner, repo)}/workflows`;
const workflowEnableApi = (owner: string, repo: string) => `${workflowsApi(owner, repo)}/enable`;
const workflowDisableApi = (owner: string, repo: string) => `${workflowsApi(owner, repo)}/disable`;
const workflowDispatchApi = (owner: string, repo: string) =>
  `${actionsBase(owner, repo)}/workflows/dispatches`;
const secretsApi = (owner: string, repo: string) => `${actionsBase(owner, repo)}/secrets`;
const secretApi = (owner: string, repo: string, name: string) =>
  `${secretsApi(owner, repo)}/${name}`;

const DISPATCH_WORKFLOW_FILE = '.gitnode/workflows/e2e-dispatch.yaml';
const DISPATCH_WORKFLOW_YAML = `name: E2E Dispatch Test
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

/** Creates a workflow YAML blob so enable/disable/dispatch tests have something to act on. */
async function createDispatchWorkflow(
  request: APIRequestContext,
  owner: string,
  repo: string,
  authorization: string,
): Promise<void> {
  const resp = await request.put(
    `/api/repos/${owner}/${repo}/blob/main/${DISPATCH_WORKFLOW_FILE}`,
    {
      headers: { Authorization: authorization },
      data: {
        content: DISPATCH_WORKFLOW_YAML,
        commitMessage: 'e2e: add dispatch workflow',
      },
    },
  );
  if (!resp.ok()) {
    throw new Error(`create workflow blob failed (${resp.status()}): ${await resp.text()}`);
  }
}

/** Creates a public repo for unauthenticated-access tests. */
async function createPublicRepo(
  request: APIRequestContext,
  authorization: string,
): Promise<{ owner: string; name: string }> {
  const name = `e2e-pub-act-${Date.now().toString(36)}`;
  const resp = await request.post('/api/repo', {
    headers: { Authorization: authorization },
    data: { name, description: 'E2E actions public repo', isPrivate: false },
  });
  if (!resp.ok())
    throw new Error(`create public repo failed (${resp.status()}): ${await resp.text()}`);
  const body = (await resp.json()) as { owner: { username: string }; name: string };
  return { owner: body.owner.username, name: body.name };
}

async function createPrivateRepo(
  request: APIRequestContext,
  authorization: string,
): Promise<{ owner: string; name: string }> {
  const name = `e2e-priv-act-${Date.now().toString(36)}`;
  const resp = await request.post('/api/repo', {
    headers: { Authorization: authorization },
    data: { name, description: 'E2E actions private repo', isPrivate: true },
  });
  if (!resp.ok())
    throw new Error(`create private repo failed (${resp.status()}): ${await resp.text()}`);
  const body = (await resp.json()) as { owner: { username: string }; name: string };
  return { owner: body.owner.username, name: body.name };
}

// ─── Runner ───────────────────────────────────────────────────────────────────

test.describe('Runner', () => {
  test('POST registration-token — returns ghrt_ token', async ({ authedRequest }) => {
    const response = await authedRequest.post(registrationTokenApi());
    expect(response.status()).toBe(200);
    const body = (await response.json()) as { token: string; expiresAt: string };
    expect(typeof body.token).toBe('string');
    expect(body.token.startsWith('ghrt_')).toBe(true);
    expect(typeof body.expiresAt).toBe('string');
  });

  test('GET runners — returns array', async ({ authedRequest }) => {
    const response = await authedRequest.get(runnersApi());
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });
});

// ─── Workflows ────────────────────────────────────────────────────────────────

test.describe('Workflows', () => {
  test('GET workflows — returns array', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(workflowsApi(api.owner, api.repo));
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test.describe.serial('enable / disable / dispatch', () => {
    test.beforeAll(async ({ authedRequest, api, session }) => {
      await createDispatchWorkflow(authedRequest, api.owner, api.repo, session.authorization);
    });

    test('GET workflows — dispatch workflow appears after blob push', async ({
      authedRequest,
      api,
    }) => {
      const response = await authedRequest.get(workflowsApi(api.owner, api.repo));
      expect(response.status()).toBe(200);
      const list = (await response.json()) as { filePath: string }[];
      expect(list.some((w) => w.filePath === DISPATCH_WORKFLOW_FILE)).toBe(true);
    });

    test('PUT workflows/disable — disables workflow', async ({ authedRequest, api }) => {
      const response = await authedRequest.put(workflowDisableApi(api.owner, api.repo), {
        params: { filePath: DISPATCH_WORKFLOW_FILE },
      });
      expect(response.status()).toBe(204);
    });

    test('PUT workflows/enable — re-enables workflow', async ({ authedRequest, api }) => {
      const response = await authedRequest.put(workflowEnableApi(api.owner, api.repo), {
        params: { filePath: DISPATCH_WORKFLOW_FILE },
      });
      expect(response.status()).toBe(204);
    });

    test('POST workflows/dispatches — triggers workflow_dispatch run', async ({
      authedRequest,
      api,
    }) => {
      const response = await authedRequest.post(workflowDispatchApi(api.owner, api.repo), {
        data: {
          filePath: DISPATCH_WORKFLOW_FILE,
          ref: 'main',
          inputs: { message: 'e2e test' },
        },
      });
      expect(response.status()).toBe(204);
    });
  });
});

// ─── Runs ─────────────────────────────────────────────────────────────────────

test.describe('Runs', () => {
  test('GET runs — returns page shape', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(runsApi(api.owner, api.repo));
    expect(response.status()).toBe(200);
    const body = (await response.json()) as { content: unknown[]; totalElements: number };
    expect(Array.isArray(body.content)).toBe(true);
    expect(typeof body.totalElements).toBe('number');
  });

  test.describe.serial('cancel / delete run', () => {
    let createdRunId: string;

    test.beforeAll(async ({ authedRequest, api, session }) => {
      // Ensure dispatch workflow exists and trigger a run
      await createDispatchWorkflow(authedRequest, api.owner, api.repo, session.authorization);
      const dispatchResp = await authedRequest.post(workflowDispatchApi(api.owner, api.repo), {
        data: { filePath: DISPATCH_WORKFLOW_FILE, ref: 'main' },
      });
      expect(dispatchResp.status()).toBe(204);

      // Fetch the latest run
      const runsResp = await authedRequest.get(runsApi(api.owner, api.repo));
      const body = (await runsResp.json()) as { content: { id: string }[] };
      expect(body.content.length).toBeGreaterThan(0);
      createdRunId = body.content[0].id;
    });

    test('GET runs/{runId} — returns run detail', async ({ authedRequest, api }) => {
      const response = await authedRequest.get(runApi(api.owner, api.repo, createdRunId));
      expect(response.status()).toBe(200);
      const run = (await response.json()) as { id: string };
      expect(run.id).toBe(createdRunId);
    });

    test('POST runs/{runId}/cancel — cancels run', async ({ authedRequest, api }) => {
      const response = await authedRequest.post(
        `${runApi(api.owner, api.repo, createdRunId)}/cancel`,
      );
      expect(response.status()).toBe(204);
    });

    test('DELETE runs/{runId} — deletes run', async ({ authedRequest, api }) => {
      const response = await authedRequest.delete(runApi(api.owner, api.repo, createdRunId));
      expect(response.status()).toBe(204);
    });
  });
});

// ─── Secrets CRUD (owner) ─────────────────────────────────────────────────────

test.describe.serial('Secrets CRUD', () => {
  const SECRET_NAME = 'E2E_TEST_SECRET';
  const SECRET_VALUE = 'super-secret-value';

  test('PUT secrets/{name} — creates secret', async ({ authedRequest, api }) => {
    const response = await authedRequest.put(secretApi(api.owner, api.repo, SECRET_NAME), {
      data: { value: SECRET_VALUE },
    });
    expect(response.status()).toBe(204);
  });

  test('GET secrets — lists secret name (value not returned)', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(secretsApi(api.owner, api.repo));
    expect(response.status()).toBe(200);
    const body = (await response.json()) as { name: string }[];
    expect(Array.isArray(body)).toBe(true);
    expect(body.some((s) => s.name === SECRET_NAME)).toBe(true);
    // Value must never be returned
    const secret = body.find((s) => s.name === SECRET_NAME)!;
    expect(Object.keys(secret)).not.toContain('value');
  });

  test('PUT secrets/{name} — updates secret', async ({ authedRequest, api }) => {
    const response = await authedRequest.put(secretApi(api.owner, api.repo, SECRET_NAME), {
      data: { value: 'updated-value' },
    });
    expect(response.status()).toBe(204);
  });

  test('DELETE secrets/{name} — deletes secret', async ({ authedRequest, api }) => {
    const response = await authedRequest.delete(secretApi(api.owner, api.repo, SECRET_NAME));
    expect(response.status()).toBe(204);
  });

  test('GET secrets — secret gone after delete', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(secretsApi(api.owner, api.repo));
    expect(response.status()).toBe(200);
    const body = (await response.json()) as { name: string }[];
    expect(body.some((s) => s.name === SECRET_NAME)).toBe(false);
  });
});

// ─── Public repo — unauthenticated read access ────────────────────────────────

test.describe('Public repo — unauthenticated read', () => {
  let pubOwner: string;
  let pubRepo: string;
  let anonRequest: APIRequestContext;

  test.beforeAll(async ({ playwright, authedRequest, session }) => {
    const pub = await createPublicRepo(authedRequest, session.authorization);
    pubOwner = pub.owner;
    pubRepo = pub.name;

    anonRequest = await playwright.request.newContext({
      baseURL: getApiBaseUrl(),
      extraHTTPHeaders: { Accept: 'application/json' },
    });
  });

  test.afterAll(async () => {
    await anonRequest?.dispose();
  });

  test('GET workflows — 200 without auth on public repo', async () => {
    const response = await anonRequest.get(workflowsApi(pubOwner, pubRepo));
    expect(response.status()).toBe(200);
  });

  test('GET runs — 200 without auth on public repo', async () => {
    const response = await anonRequest.get(runsApi(pubOwner, pubRepo));
    expect(response.status()).toBe(200);
  });
});

// ─── Private repo — unauthenticated blocked ───────────────────────────────────

test.describe('Private repo — unauthenticated blocked', () => {
  let privOwner: string;
  let privRepo: string;
  let anonRequest: APIRequestContext;

  test.beforeAll(async ({ playwright, authedRequest, session }) => {
    const priv = await createPrivateRepo(authedRequest, session.authorization);
    privOwner = priv.owner;
    privRepo = priv.name;

    anonRequest = await playwright.request.newContext({
      baseURL: getApiBaseUrl(),
      extraHTTPHeaders: { Accept: 'application/json' },
    });
  });

  test.afterAll(async () => {
    await anonRequest?.dispose();
  });

  test('GET workflows — 403 without auth on private repo', async () => {
    const response = await anonRequest.get(workflowsApi(privOwner, privRepo));
    expect(response.status()).toBe(403);
  });

  test('GET runs — 403 without auth on private repo', async () => {
    const response = await anonRequest.get(runsApi(privOwner, privRepo));
    expect(response.status()).toBe(403);
  });
});
