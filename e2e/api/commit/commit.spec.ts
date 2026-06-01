import { expect, test } from '../fixtures/authenticated-api';
import { repoCommitsApi } from '@helpers/paths';

test.describe.serial('Commit API — all endpoints', () => {
  let headSha: string;

  test('GET /api/repos/{owner}/{repo}/commits', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(repoCommitsApi(api.owner, api.repo), {
      params: { branch: 'main', page: '0', size: '10' },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.items.length).toBeGreaterThan(0);
    headSha = body.items[0].sha;
  });

  test('GET /api/repos/{owner}/{repo}/commits/{sha}', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(
      `${repoCommitsApi(api.owner, api.repo)}/${headSha}`,
    );
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.sha).toBe(headSha);
  });

  test('GET /api/repos/{owner}/{repo}/commits/{sha}/diff', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(
      `${repoCommitsApi(api.owner, api.repo)}/${headSha}/diff`,
    );
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });
});
