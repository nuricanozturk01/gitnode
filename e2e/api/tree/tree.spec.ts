import { expect, test } from '../fixtures/authenticated-api';
import { reposApi } from '@helpers/paths';

test.describe.serial('Tree API — all endpoints', () => {
  test('GET /api/repos/{owner}/{repo}/tree/{branch}', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(
      `${reposApi(api.owner, api.repo)}/tree/main`,
    );
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.entries).toBeDefined();
  });

  test('GET /api/repos/{owner}/{repo}/blob/{branch}/**', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(
      `${reposApi(api.owner, api.repo)}/blob/main/README.md`,
    );
    expect(response.status()).toBe(200);
    const body = await response.json();
    const text = body.isBinary
      ? body.content
      : Buffer.from(body.content, 'base64').toString('utf8');
    expect(text).toContain('E2E');
  });

  test('GET /api/repos/{owner}/{repo}/raw/{branch}/**', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(
      `${reposApi(api.owner, api.repo)}/raw/main/README.md`,
    );
    expect(response.status()).toBe(200);
    const text = await response.text();
    expect(text.toLowerCase()).toMatch(/e2e|fixture/);
  });

  test('GET /api/repos/{owner}/{repo}/languages', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(
      `${reposApi(api.owner, api.repo)}/languages`,
      { params: { branch: 'main' } },
    );
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('GET /api/repos/{owner}/{repo}/archive/{branch}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(
      `${reposApi(api.owner, api.repo)}/archive/main`,
    );
    expect(response.status()).toBe(200);
    expect(response.headers()['content-type']).toContain('zip');
  });

  test('PUT /api/repos/{owner}/{repo}/blob/{branch}/**', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.put(
      `${reposApi(api.owner, api.repo)}/blob/main/docs/e2e.txt`,
      {
        data: {
          content: 'tree api e2e\n',
          commitMessage: 'e2e: add docs/e2e.txt',
        },
      },
    );
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.path).toContain('docs/e2e.txt');
  });
});
