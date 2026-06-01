import { snippetsApi } from '@helpers/paths';

import { expect, test } from '../fixtures/authenticated-api';

test.describe.serial('Snippet API — all endpoints', () => {
  let snippetId: string;
  let fileId: string;
  let revisionId: string;
  let forkId: string;
  let commentId: string;

  test('POST /api/snippets', async ({ authedRequest }) => {
    const response = await authedRequest.post(snippetsApi, {
      data: {
        title: 'E2E snippet',
        description: 'API test',
        visibility: 'PUBLIC',
        files: [{ filename: 'main.ts', content: 'export const x = 1;\n' }],
      },
    });
    expect(response.status()).toBe(201);
    const body = await response.json();
    snippetId = body.id;
    fileId = body.files[0].id;
  });

  test('GET /api/snippets', async ({ authedRequest }) => {
    const response = await authedRequest.get(snippetsApi, {
      params: { page: '0', size: '20' },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.content.length).toBeGreaterThan(0);
  });

  test('GET /api/snippets/me', async ({ authedRequest }) => {
    const response = await authedRequest.get(`${snippetsApi}/me`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.content.some((s: { id: string }) => s.id === snippetId)).toBe(true);
  });

  test('GET /api/snippets/{snippetId}', async ({ authedRequest }) => {
    const response = await authedRequest.get(`${snippetsApi}/${snippetId}`);
    expect(response.status()).toBe(200);
    expect((await response.json()).id).toBe(snippetId);
  });

  test('PATCH /api/snippets/{snippetId}', async ({ authedRequest }) => {
    const response = await authedRequest.patch(`${snippetsApi}/${snippetId}`, {
      data: { title: 'E2E snippet updated' },
    });
    expect(response.status()).toBe(200);
    expect((await response.json()).title).toBe('E2E snippet updated');
  });

  test('GET /api/snippets/{snippetId}/revisions', async ({ authedRequest }) => {
    const response = await authedRequest.get(`${snippetsApi}/${snippetId}/revisions`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.content.length).toBeGreaterThan(0);
    revisionId = body.content[0].id;
  });

  test('GET /api/snippets/{snippetId}/revisions/{revisionId}', async ({ authedRequest }) => {
    const response = await authedRequest.get(`${snippetsApi}/${snippetId}/revisions/${revisionId}`);
    expect(response.status()).toBe(200);
    expect((await response.json()).id).toBe(revisionId);
  });

  test('GET /api/snippets/{snippetId}/files/{fileId}/raw', async ({ authedRequest }) => {
    const response = await authedRequest.get(`${snippetsApi}/${snippetId}/files/${fileId}/raw`);
    expect(response.status()).toBe(200);
    expect(await response.text()).toContain('export');
  });

  test('POST /api/snippets/{snippetId}/fork', async ({ authedRequest }) => {
    const response = await authedRequest.post(`${snippetsApi}/${snippetId}/fork`);
    expect(response.status()).toBe(201);
    forkId = (await response.json()).id;
  });

  test('PUT /api/snippets/{snippetId}/repo/{repoId}', async ({ authedRequest, session }) => {
    const response = await authedRequest.put(`${snippetsApi}/${snippetId}/repo/${session.repoId}`);
    expect(response.status()).toBe(200);
  });

  test('GET /api/snippets/by-owner/{username}', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(`${snippetsApi}/by-owner/${api.owner}`);
    expect(response.status()).toBe(200);
    expect((await response.json()).content.length).toBeGreaterThan(0);
  });

  test('GET /api/snippets/repo/{owner}/{repoName}', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(`${snippetsApi}/repo/${api.owner}/${api.repo}`);
    expect(response.status()).toBe(200);
    expect(Array.isArray((await response.json()).content)).toBe(true);
  });

  test('GET /api/snippets/{snippetId}/comments', async ({ authedRequest }) => {
    const response = await authedRequest.get(`${snippetsApi}/${snippetId}/comments`);
    expect(response.status()).toBe(200);
    expect((await response.json()).content).toBeDefined();
  });

  test('POST /api/snippets/{snippetId}/comments', async ({ authedRequest }) => {
    const response = await authedRequest.post(`${snippetsApi}/${snippetId}/comments`, {
      data: { body: 'E2E comment' },
    });
    expect(response.status()).toBe(201);
    commentId = (await response.json()).id;
  });

  test('DELETE /api/snippets/{snippetId}/comments/{commentId}', async ({ authedRequest }) => {
    const response = await authedRequest.delete(
      `${snippetsApi}/${snippetId}/comments/${commentId}`,
    );
    expect(response.status()).toBe(204);
  });

  test('DELETE /api/snippets/{snippetId}/repo/{repoId}', async ({ authedRequest, session }) => {
    const response = await authedRequest.delete(
      `${snippetsApi}/${snippetId}/repo/${session.repoId}`,
    );
    expect(response.status()).toBe(200);
  });

  test('DELETE /api/snippets/{snippetId} (fork)', async ({ authedRequest }) => {
    const response = await authedRequest.delete(`${snippetsApi}/${forkId}`);
    expect(response.status()).toBe(204);
  });

  test('DELETE /api/snippets/{snippetId}', async ({ authedRequest }) => {
    const response = await authedRequest.delete(`${snippetsApi}/${snippetId}`);
    expect(response.status()).toBe(204);
  });
});
