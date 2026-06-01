import { expect, test } from '../fixtures/authenticated-api';
import {
  projectApi,
  projectBoardsApi,
  projectTasksApi,
  projectsApi,
} from '@helpers/paths';

test.describe.serial('Task module API — all endpoints', () => {
  const extraProjectCode = `X${Date.now().toString(36).slice(-3).toUpperCase()}`;
  let boardId: string;
  let columnId: string;
  let taskCode: string;
  let subtaskId: string;

  test('GET /api/projects/{owner}', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(projectsApi(api.owner));
    expect(response.status()).toBe(200);
    expect((await response.json()).content.length).toBeGreaterThan(0);
  });

  test('GET /api/projects/{owner}/{projectCode}', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(projectApi(api.owner, api.projectCode));
    expect(response.status()).toBe(200);
    expect((await response.json()).codePrefix).toBe(api.projectCode);
  });

  test('POST /api/projects/{owner}', async ({ authedRequest, api }) => {
    const response = await authedRequest.post(projectsApi(api.owner), {
      data: {
        name: 'E2E extra project',
        description: 'Disposable',
        codePrefix: extraProjectCode,
        isPublic: true,
      },
    });
    expect(response.status()).toBe(201);
    expect((await response.json()).codePrefix).toBe(extraProjectCode);
  });

  test('PATCH /api/projects/{owner}/{projectCode}', async ({ authedRequest, api }) => {
    const response = await authedRequest.patch(projectApi(api.owner, api.projectCode), {
      data: { name: 'E2E Project (patched)' },
    });
    expect(response.status()).toBe(200);
  });

  test('POST /api/projects/{owner}/{projectCode}/repos/{repoId}', async ({
    authedRequest,
    api,
    session,
  }) => {
    const response = await authedRequest.post(
      `${projectApi(api.owner, api.projectCode)}/repos/${session.repoId}`,
    );
    expect(response.status()).toBe(204);
  });

  test('GET /api/projects/{owner}/{projectCode}/repos', async ({
    authedRequest,
    api,
    session,
  }) => {
    const response = await authedRequest.get(
      `${projectApi(api.owner, api.projectCode)}/repos`,
    );
    expect(response.status()).toBe(200);
    const repos = await response.json();
    expect(repos.some((r: { id: string }) => r.id === session.repoId)).toBe(true);
  });

  test('GET /api/projects/{owner}/by-repo/{repoId}', async ({
    authedRequest,
    api,
    session,
  }) => {
    const response = await authedRequest.get(
      `${projectsApi(api.owner)}/by-repo/${session.repoId}`,
    );
    expect(response.status()).toBe(200);
    expect((await response.json()).content.length).toBeGreaterThan(0);
  });

  test('POST /api/projects/{owner}/{projectCode}/boards', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.post(
      projectBoardsApi(api.owner, api.projectCode),
      { data: { name: 'E2E Board', position: 0 } },
    );
    expect(response.status()).toBe(201);
    boardId = (await response.json()).id;
  });

  test('GET /api/projects/{owner}/{projectCode}/boards', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(projectBoardsApi(api.owner, api.projectCode));
    expect(response.status()).toBe(200);
    expect((await response.json()).some((b: { id: string }) => b.id === boardId)).toBe(
      true,
    );
  });

  test('GET /api/projects/{owner}/{projectCode}/boards/{boardId}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(
      `${projectBoardsApi(api.owner, api.projectCode)}/${boardId}`,
    );
    expect(response.status()).toBe(200);
    expect((await response.json()).id).toBe(boardId);
  });

  test('PATCH /api/projects/{owner}/{projectCode}/boards/{boardId}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.patch(
      `${projectBoardsApi(api.owner, api.projectCode)}/${boardId}`,
      { data: { name: 'E2E Board patched' } },
    );
    expect(response.status()).toBe(200);
  });

  test('POST /api/projects/{owner}/{projectCode}/boards/{boardId}/columns', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.post(
      `${projectBoardsApi(api.owner, api.projectCode)}/${boardId}/columns`,
      { data: { name: 'Todo', position: 0, color: '#ccc' } },
    );
    expect(response.status()).toBe(201);
    columnId = (await response.json()).id;
  });

  test('PATCH /api/projects/.../boards/{boardId}/columns/{columnId}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.patch(
      `${projectBoardsApi(api.owner, api.projectCode)}/${boardId}/columns/${columnId}`,
      { data: { name: 'In progress' } },
    );
    expect(response.status()).toBe(200);
  });

  test('POST /api/projects/{owner}/{projectCode}/tasks', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.post(
      projectTasksApi(api.owner, api.projectCode),
      {
        data: {
          title: 'E2E task',
          description: 'API',
          boardColumnId: columnId,
          type: 'TASK',
          position: 0,
        },
      },
    );
    expect(response.status()).toBe(201);
    taskCode = (await response.json()).code;
  });

  test('GET /api/projects/{owner}/{projectCode}/tasks', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(projectTasksApi(api.owner, api.projectCode));
    expect(response.status()).toBe(200);
    expect((await response.json()).content.length).toBeGreaterThan(0);
  });

  test('GET /api/projects/{owner}/{projectCode}/tasks/{taskCode}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(
      `${projectTasksApi(api.owner, api.projectCode)}/${taskCode}`,
    );
    expect(response.status()).toBe(200);
    expect((await response.json()).code).toBe(taskCode);
  });

  test('PATCH /api/projects/{owner}/{projectCode}/tasks/{taskCode}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.patch(
      `${projectTasksApi(api.owner, api.projectCode)}/${taskCode}`,
      { data: { title: 'E2E task patched' } },
    );
    expect(response.status()).toBe(200);
  });

  test('POST /api/projects/.../tasks/{taskCode}/subtasks', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.post(
      `${projectTasksApi(api.owner, api.projectCode)}/${taskCode}/subtasks`,
      { data: { title: 'Subtask 1', position: 0 } },
    );
    expect(response.status()).toBe(201);
    subtaskId = (await response.json()).id;
  });

  test('PATCH /api/projects/.../tasks/{taskCode}/subtasks/{subtaskId}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.patch(
      `${projectTasksApi(api.owner, api.projectCode)}/${taskCode}/subtasks/${subtaskId}`,
      { data: { title: 'Subtask done' } },
    );
    expect(response.status()).toBe(200);
  });

  test('POST /api/projects/.../tasks/{taskCode}/subtasks/{subtaskId}/branch', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.post(
      `${projectTasksApi(api.owner, api.projectCode)}/${taskCode}/subtasks/${subtaskId}/branch`,
      { data: { repoOwner: api.owner, repoName: api.repo, sourceBranch: 'main' } },
    );
    expect(response.status()).toBe(201);
  });

  test('POST /api/projects/.../tasks/{taskCode}/branch', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.post(
      `${projectTasksApi(api.owner, api.projectCode)}/${taskCode}/branch`,
      { data: { repoOwner: api.owner, repoName: api.repo, sourceBranch: 'main' } },
    );
    expect(response.status()).toBe(201);
    expect((await response.json()).name).toBeTruthy();
  });

  test('DELETE /api/projects/.../tasks/{taskCode}/subtasks/{subtaskId}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.delete(
      `${projectTasksApi(api.owner, api.projectCode)}/${taskCode}/subtasks/${subtaskId}`,
    );
    expect(response.status()).toBe(204);
  });

  test('DELETE /api/projects/{owner}/{projectCode}/tasks/{taskCode}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.delete(
      `${projectTasksApi(api.owner, api.projectCode)}/${taskCode}`,
    );
    expect(response.status()).toBe(204);
  });

  test('DELETE /api/projects/.../boards/{boardId}/columns/{columnId}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.delete(
      `${projectBoardsApi(api.owner, api.projectCode)}/${boardId}/columns/${columnId}`,
    );
    expect(response.status()).toBe(204);
  });

  test('DELETE /api/projects/{owner}/{projectCode}/boards/{boardId}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.delete(
      `${projectBoardsApi(api.owner, api.projectCode)}/${boardId}`,
    );
    expect(response.status()).toBe(204);
  });

  test('DELETE /api/projects/{owner}/{projectCode}/repos/{repoId}', async ({
    authedRequest,
    api,
    session,
  }) => {
    const response = await authedRequest.delete(
      `${projectApi(api.owner, api.projectCode)}/repos/${session.repoId}`,
    );
    expect(response.status()).toBe(204);
  });

  test('DELETE /api/projects/{owner}/{projectCode} (extra)', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.delete(
      projectApi(api.owner, extraProjectCode),
    );
    expect(response.status()).toBe(204);
  });
});
