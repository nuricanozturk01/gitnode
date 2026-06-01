import { expect, test } from '../fixtures/authenticated-api';
import { repoReleasesApi, repoTagsApi } from '@helpers/paths';

test.describe.serial('Tag & release API — all endpoints', () => {
  const tagName = `v1.0-e2e-${Date.now().toString(36)}`;
  let headSha: string;
  let releaseId: string;

  test.beforeAll(async ({ authedRequest, api }) => {
    const commits = await authedRequest.get(
      `/api/repos/${api.owner}/${api.repo}/commits`,
      { params: { branch: 'main', page: '0', size: '1' } },
    );
    const body = await commits.json();
    headSha = body.items[0].sha;
  });

  test('GET /api/repos/{owner}/{repo}/tags', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(repoTagsApi(api.owner, api.repo));
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('POST /api/repos/{owner}/{repo}/tags', async ({ authedRequest, api }) => {
    const response = await authedRequest.post(repoTagsApi(api.owner, api.repo), {
      data: { name: tagName, sha: headSha, message: 'E2E tag' },
    });
    expect(response.status()).toBe(201);
  });

  test('GET /api/repos/{owner}/{repo}/tags/{tag}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(
      `${repoTagsApi(api.owner, api.repo)}/${tagName}`,
    );
    expect(response.status()).toBe(200);
    expect((await response.json()).name).toBe(tagName);
  });

  test('GET /api/repos/{owner}/{repo}/releases', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(repoReleasesApi(api.owner, api.repo));
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('POST /api/repos/{owner}/{repo}/releases', async ({ authedRequest, api }) => {
    const response = await authedRequest.post(repoReleasesApi(api.owner, api.repo), {
      data: {
        tagName,
        name: 'E2E Release',
        body: 'Release notes',
        isDraft: false,
        isPrerelease: false,
        createNewTag: false,
      },
    });
    expect(response.status()).toBe(201);
    releaseId = (await response.json()).id;
  });

  test('GET /api/repos/{owner}/{repo}/releases/{id}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(
      `${repoReleasesApi(api.owner, api.repo)}/${releaseId}`,
    );
    expect(response.status()).toBe(200);
    expect((await response.json()).id).toBe(releaseId);
  });

  test('GET /api/repos/{owner}/{repo}/releases/latest', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(
      `${repoReleasesApi(api.owner, api.repo)}/latest`,
    );
    expect([200, 404]).toContain(response.status());
  });

  test('GET /api/repos/{owner}/{repo}/releases/tag/{tagName}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(
      `${repoReleasesApi(api.owner, api.repo)}/tag/${tagName}`,
    );
    expect(response.status()).toBe(200);
  });

  test('PATCH /api/repos/{owner}/{repo}/releases/{id}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.patch(
      `${repoReleasesApi(api.owner, api.repo)}/${releaseId}`,
      { data: { name: 'E2E Release patched' } },
    );
    expect(response.status()).toBe(200);
  });

  test('DELETE /api/repos/{owner}/{repo}/releases/{id}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.delete(
      `${repoReleasesApi(api.owner, api.repo)}/${releaseId}`,
    );
    expect(response.status()).toBe(204);
  });

  test('DELETE /api/repos/{owner}/{repo}/tags/{tag}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.delete(
      `${repoTagsApi(api.owner, api.repo)}/${tagName}`,
    );
    expect(response.status()).toBe(204);
  });
});
