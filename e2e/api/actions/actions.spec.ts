import { expect, test } from '../fixtures/authenticated-api';

const actionsBase = (owner: string, repo: string) =>
  `/api/repos/${owner}/${repo}/actions`;

const runnersApi = (owner: string, repo: string) =>
  `${actionsBase(owner, repo)}/runners`;

const registrationTokenApi = (owner: string, repo: string) =>
  `${runnersApi(owner, repo)}/registration-token`;

const runsApi = (owner: string, repo: string) =>
  `${actionsBase(owner, repo)}/runs`;

const workflowsApi = (owner: string, repo: string) =>
  `${actionsBase(owner, repo)}/workflows`;

const secretsApi = (owner: string, repo: string) =>
  `${actionsBase(owner, repo)}/secrets`;

const secretApi = (owner: string, repo: string, name: string) =>
  `${secretsApi(owner, repo)}/${name}`;

test.describe('Actions API', () => {
  test('POST /api/repos/{owner}/{repo}/actions/runners/registration-token — returns token', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.post(
      registrationTokenApi(api.owner, api.repo),
    );
    expect(response.status()).toBe(200);
    const body = (await response.json()) as { token: string; expiresAt: string };
    expect(typeof body.token).toBe('string');
    expect(body.token.startsWith('ghrt_')).toBe(true);
    expect(typeof body.expiresAt).toBe('string');
  });

  test('GET /api/repos/{owner}/{repo}/actions/runners — returns array', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(runnersApi(api.owner, api.repo));
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('GET /api/repos/{owner}/{repo}/actions/runs — returns page', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(runsApi(api.owner, api.repo));
    expect(response.status()).toBe(200);
  });

  test('GET /api/repos/{owner}/{repo}/actions/workflows — returns array', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(workflowsApi(api.owner, api.repo));
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test.describe.serial('Secrets CRUD', () => {
    const SECRET_NAME = 'TEST_SECRET';

    test('PUT /api/repos/{owner}/{repo}/actions/secrets/{name} — creates secret', async ({
      authedRequest,
      api,
    }) => {
      const response = await authedRequest.put(
        secretApi(api.owner, api.repo, SECRET_NAME),
        { data: { value: 'hello' } },
      );
      expect(response.status()).toBe(204);
    });

    test('GET /api/repos/{owner}/{repo}/actions/secrets — lists secret', async ({
      authedRequest,
      api,
    }) => {
      const response = await authedRequest.get(secretsApi(api.owner, api.repo));
      expect(response.status()).toBe(200);
      const body = (await response.json()) as Array<{ name: string }>;
      expect(Array.isArray(body)).toBe(true);
      expect(body.some((s) => s.name === SECRET_NAME)).toBe(true);
    });

    test('DELETE /api/repos/{owner}/{repo}/actions/secrets/{name} — deletes secret', async ({
      authedRequest,
      api,
    }) => {
      const response = await authedRequest.delete(
        secretApi(api.owner, api.repo, SECRET_NAME),
      );
      expect(response.status()).toBe(204);
    });
  });
});
