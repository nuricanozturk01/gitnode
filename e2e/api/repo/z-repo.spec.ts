import { repoApi } from '@helpers/paths';

import { expect, test } from '../fixtures/authenticated-api';

test.describe.serial('Repo API — all endpoints', () => {
  const disposableName = `e2e-disposable-${Date.now().toString(36)}`;

  test('POST /api/repo', async ({ authedRequest }) => {
    const response = await authedRequest.post(repoApi, {
      data: { name: disposableName, description: 'Disposable', isPrivate: false },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.name).toBe(disposableName);
  });

  test('GET /api/repo/{owner}/{repo}', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(`${repoApi}/${api.owner}/${api.repo}`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.name).toBe(api.repo);
    expect(body.defaultBranch).toBe('main');
  });

  test('GET /api/repo/{owner}', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(`${repoApi}/${api.owner}`, {
      params: { page: '0', size: '50' },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.content.some((r: { name: string }) => r.name === api.repo)).toBe(true);
  });

  test('PATCH /api/repo/{owner}/{repo}', async ({ authedRequest, api }) => {
    const response = await authedRequest.patch(`${repoApi}/${api.owner}/${api.repo}`, {
      data: { name: api.repo, description: 'E2E patched description' },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.description).toBe('E2E patched description');
  });

  test('DELETE /api/repo/{owner}/{repo} (disposable)', async ({ authedRequest, api }) => {
    const response = await authedRequest.delete(`${repoApi}/${api.owner}/${disposableName}`);
    expect(response.status()).toBe(204);
    const list = await authedRequest.get(`${repoApi}/${api.owner}`, {
      params: { page: '0', size: '50' },
    });
    const names = (await list.json()).content.map((r: { name: string }) => r.name);
    expect(names).not.toContain(disposableName);
    expect(names).toContain(api.repo);
  });
});
