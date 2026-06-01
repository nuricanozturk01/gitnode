import { repoBranchesApi } from '@helpers/paths';

import { expect, test } from '../fixtures/authenticated-api';

test.describe.serial('Branch API — all endpoints', () => {
  const featureBranch = `e2e-feature-${Date.now().toString(36)}`;

  test('GET /api/repos/{owner}/{repo}/branches', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(repoBranchesApi(api.owner, api.repo));
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.some((b: { name: string }) => b.name === 'main')).toBe(true);
  });

  test('GET /api/repos/{owner}/{repo}/branches/{branch}', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(`${repoBranchesApi(api.owner, api.repo)}/main`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.name).toBe('main');
  });

  test('POST /api/repos/{owner}/{repo}/branches', async ({ authedRequest, api }) => {
    const response = await authedRequest.post(repoBranchesApi(api.owner, api.repo), {
      data: { name: featureBranch, sourceBranch: 'main' },
    });
    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body.name).toBe(featureBranch);
  });

  test('PATCH /api/repos/{owner}/{repo}/branches/default', async ({ authedRequest, api }) => {
    const response = await authedRequest.patch(`${repoBranchesApi(api.owner, api.repo)}/default`, {
      data: { branchName: 'main' },
    });
    expect(response.status()).toBe(204);
  });

  test('DELETE /api/repos/{owner}/{repo}/branches/{branch}', async ({ authedRequest, api }) => {
    const response = await authedRequest.delete(
      `${repoBranchesApi(api.owner, api.repo)}/${featureBranch}`,
    );
    expect(response.status()).toBe(204);
  });
});
