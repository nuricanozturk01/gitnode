import { repoIssuesApi } from '@helpers/paths';

import { expect, test } from '../fixtures/authenticated-api';

test.describe.serial('Issue API — all endpoints', () => {
  let issueNumber: number;
  let commentId: string;

  test('POST /api/repos/{owner}/{repo}/issues', async ({ authedRequest, api }) => {
    const response = await authedRequest.post(repoIssuesApi(api.owner, api.repo), {
      data: { title: 'E2E issue', description: 'From API test' },
    });
    expect(response.status()).toBe(201);
    issueNumber = (await response.json()).number;
  });

  test('GET /api/repos/{owner}/{repo}/issues', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(repoIssuesApi(api.owner, api.repo), {
      params: { status: 'OPEN', page: '0' },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.content.some((i: { number: number }) => i.number === issueNumber)).toBe(true);
  });

  test('GET /api/repos/{owner}/{repo}/issues/{number}', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(
      `${repoIssuesApi(api.owner, api.repo)}/${issueNumber}`,
    );
    expect(response.status()).toBe(200);
    expect((await response.json()).number).toBe(issueNumber);
  });

  test('PATCH /api/repos/{owner}/{repo}/issues/{number}', async ({ authedRequest, api }) => {
    const response = await authedRequest.patch(
      `${repoIssuesApi(api.owner, api.repo)}/${issueNumber}`,
      { data: { title: 'E2E issue updated', status: 'OPEN' } },
    );
    expect(response.status()).toBe(200);
    expect((await response.json()).title).toBe('E2E issue updated');
  });

  test('GET /api/repos/{owner}/{repo}/issues/{number}/linked-tasks', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(
      `${repoIssuesApi(api.owner, api.repo)}/${issueNumber}/linked-tasks`,
    );
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('GET /api/repos/{owner}/{repo}/issues/{number}/comments', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(
      `${repoIssuesApi(api.owner, api.repo)}/${issueNumber}/comments`,
    );
    expect(response.status()).toBe(200);
    expect((await response.json()).content).toBeDefined();
  });

  test('POST /api/repos/{owner}/{repo}/issues/{number}/comments', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.post(
      `${repoIssuesApi(api.owner, api.repo)}/${issueNumber}/comments`,
      { data: { body: 'Issue comment E2E' } },
    );
    expect(response.status()).toBe(201);
    commentId = (await response.json()).id;
  });

  test('PATCH /api/repos/{owner}/{repo}/issues/{number}/comments/{commentId}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.patch(
      `${repoIssuesApi(api.owner, api.repo)}/${issueNumber}/comments/${commentId}`,
      { data: { body: 'Updated comment' } },
    );
    expect(response.status()).toBe(200);
    expect((await response.json()).body).toBe('Updated comment');
  });

  test('DELETE /api/repos/{owner}/{repo}/issues/{number}/comments/{commentId}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.delete(
      `${repoIssuesApi(api.owner, api.repo)}/${issueNumber}/comments/${commentId}`,
    );
    expect(response.status()).toBe(204);
  });

  test('DELETE /api/repos/{owner}/{repo}/issues/{number}', async ({ authedRequest, api }) => {
    const response = await authedRequest.delete(
      `${repoIssuesApi(api.owner, api.repo)}/${issueNumber}`,
    );
    expect(response.status()).toBe(204);
  });
});
