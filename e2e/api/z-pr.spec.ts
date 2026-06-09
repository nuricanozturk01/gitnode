import { repoBranchesApi, repoPullApi, repoPullsApi, reposApi } from '@helpers/paths';

import { expect, test } from './fixtures/authenticated-api';

test.describe.serial('Pull request API — all endpoints', () => {
  const featureBranch = `e2e-pr-${Date.now().toString(36)}`;
  let prNumber: number;
  let commentId: string;

  test.beforeAll(async ({ authedRequest, api }) => {
    await authedRequest.post(repoBranchesApi(api.owner, api.repo), {
      data: { name: featureBranch, sourceBranch: 'main' },
    });
    await authedRequest.put(
      `${reposApi(api.owner, api.repo)}/blob/${featureBranch}/pr-feature.txt`,
      {
        data: {
          content: 'pr feature\n',
          commitMessage: 'e2e: pr feature file',
        },
      },
    );
  });

  test('POST /api/repos/{owner}/{repo}/pulls', async ({ authedRequest, api }) => {
    const response = await authedRequest.post(repoPullsApi(api.owner, api.repo), {
      data: {
        title: 'E2E PR',
        description: 'API test',
        sourceBranch: featureBranch,
        targetBranch: 'main',
        isDraft: false,
      },
    });
    expect(response.status()).toBe(201);
    prNumber = (await response.json()).number;
  });

  test('GET /api/repos/{owner}/{repo}/pulls', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(repoPullsApi(api.owner, api.repo), {
      params: { status: 'OPEN', page: '0', size: '25' },
    });
    expect(response.status()).toBe(200);
    expect((await response.json()).content.length).toBeGreaterThan(0);
  });

  test('GET /api/repos/{owner}/{repo}/pulls/{number}', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(repoPullApi(api.owner, api.repo, prNumber));
    expect(response.status()).toBe(200);
    expect((await response.json()).number).toBe(prNumber);
  });

  test('PATCH /api/repos/{owner}/{repo}/pulls/{number}', async ({ authedRequest, api }) => {
    const response = await authedRequest.patch(repoPullApi(api.owner, api.repo, prNumber), {
      data: { title: 'E2E PR updated', isDraft: true },
    });
    expect(response.status()).toBe(200);
  });

  test('GET /api/repos/{owner}/{repo}/pulls/{number}/commits', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(
      `${repoPullApi(api.owner, api.repo, prNumber)}/commits`,
    );
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('GET /api/repos/{owner}/{repo}/pulls/{number}/diff', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(`${repoPullApi(api.owner, api.repo, prNumber)}/diff`);
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('GET /api/repos/{owner}/{repo}/pulls/{number}/comments', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(
      `${repoPullApi(api.owner, api.repo, prNumber)}/comments`,
    );
    expect(response.status()).toBe(200);
  });

  test('POST /api/repos/{owner}/{repo}/pulls/{number}/comments', async ({ authedRequest, api }) => {
    const response = await authedRequest.post(
      `${repoPullApi(api.owner, api.repo, prNumber)}/comments`,
      { data: { body: 'PR review comment' } },
    );
    expect(response.status()).toBe(201);
    commentId = (await response.json()).id;
  });

  test('PATCH /api/repos/.../pulls/{number}/comments/{commentId}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.patch(
      `${repoPullApi(api.owner, api.repo, prNumber)}/comments/${commentId}`,
      { data: { body: 'Updated PR comment' } },
    );
    expect(response.status()).toBe(200);
  });

  test('DELETE /api/repos/.../pulls/{number}/comments/{commentId}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.delete(
      `${repoPullApi(api.owner, api.repo, prNumber)}/comments/${commentId}`,
    );
    expect(response.status()).toBe(204);
  });

  test('PATCH pull open for merge', async ({ authedRequest, api }) => {
    const response = await authedRequest.patch(repoPullApi(api.owner, api.repo, prNumber), {
      data: { isDraft: false },
    });
    expect(response.status()).toBe(200);
  });

  test('POST /api/repos/{owner}/{repo}/pulls/{number}/merge', async ({ authedRequest, api }) => {
    const response = await authedRequest.post(
      `${repoPullApi(api.owner, api.repo, prNumber)}/merge`,
      { data: { strategy: 'MERGE_COMMIT', commitMessage: 'Merge E2E PR' } },
    );
    expect(response.status()).toBe(200);
    expect((await response.json()).status).toBe('MERGED');
  });

  test('DELETE /api/repos/{owner}/{repo}/pulls/{number} (close)', async ({
    authedRequest,
    api,
  }) => {
    const branchName = `e2e-close-pr-${Date.now().toString(36)}`;
    await authedRequest.post(repoBranchesApi(api.owner, api.repo), {
      data: { name: branchName, sourceBranch: 'main' },
    });
    const create = await authedRequest.post(repoPullsApi(api.owner, api.repo), {
      data: {
        title: 'To close',
        sourceBranch: branchName,
        targetBranch: 'main',
        isDraft: true,
      },
    });
    const num = (await create.json()).number;
    const response = await authedRequest.delete(repoPullApi(api.owner, api.repo, num));
    expect(response.status()).toBe(204);
  });
});
