import { expect, test } from '../fixtures/authenticated-api';
import {
  projectWebhooksApi,
  repoWebhooksApi,
  userWebhooksApi,
} from '@helpers/paths';

test.describe.serial('Webhook API — all endpoints', () => {
  let repoWebhookId: string;
  let userWebhookId: string;
  let projectWebhookId: string;

  const repoWebhookPayload = {
    url: 'https://example.com/hooks/repo-e2e',
    secret: 'e2e-secret',
    enabled: true,
    events: ['REPO_PUSHED'],
  };

  const userWebhookPayload = {
    url: 'https://example.com/hooks/user-e2e',
    secret: 'e2e-secret',
    enabled: true,
    events: ['SNIPPET_CREATED'],
  };

  const projectWebhookPayload = {
    url: 'https://example.com/hooks/project-e2e',
    secret: 'e2e-secret',
    enabled: true,
    events: ['TASK_CREATED'],
  };

  test('GET /api/repos/.../settings/webhooks', async ({ authedRequest, api }) => {
    const response = await authedRequest.get(repoWebhooksApi(api.owner, api.repo));
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('POST /api/repos/.../settings/webhooks', async ({ authedRequest, api }) => {
    const response = await authedRequest.post(repoWebhooksApi(api.owner, api.repo), {
      data: repoWebhookPayload,
    });
    expect(response.status()).toBe(201);
    repoWebhookId = (await response.json()).id;
  });

  test('PATCH /api/repos/.../settings/webhooks/{id}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.patch(
      `${repoWebhooksApi(api.owner, api.repo)}/${repoWebhookId}`,
      { data: { ...repoWebhookPayload, enabled: false } },
    );
    expect(response.status()).toBe(200);
  });

  test('DELETE /api/repos/.../settings/webhooks/{id}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.delete(
      `${repoWebhooksApi(api.owner, api.repo)}/${repoWebhookId}`,
    );
    expect(response.status()).toBe(204);
  });

  test('GET /api/users/{username}/settings/webhooks', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(userWebhooksApi(api.owner));
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('POST /api/users/{username}/settings/webhooks', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.post(userWebhooksApi(api.owner), {
      data: userWebhookPayload,
    });
    expect(response.status()).toBe(201);
    userWebhookId = (await response.json()).id;
  });

  test('PATCH /api/users/{username}/settings/webhooks/{id}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.patch(
      `${userWebhooksApi(api.owner)}/${userWebhookId}`,
      {
        data: {
          ...userWebhookPayload,
          url: 'https://example.com/hooks/user-e2e-patched',
        },
      },
    );
    expect(response.status()).toBe(200);
  });

  test('DELETE /api/users/{username}/settings/webhooks/{id}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.delete(
      `${userWebhooksApi(api.owner)}/${userWebhookId}`,
    );
    expect(response.status()).toBe(204);
  });

  test('GET /api/{owner}/projects/{code}/settings/webhooks', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.get(
      projectWebhooksApi(api.owner, api.projectCode),
    );
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('POST /api/{owner}/projects/{code}/settings/webhooks', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.post(
      projectWebhooksApi(api.owner, api.projectCode),
      { data: projectWebhookPayload },
    );
    expect(response.status()).toBe(201);
    projectWebhookId = (await response.json()).id;
  });

  test('PATCH /api/{owner}/projects/{code}/settings/webhooks/{id}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.patch(
      `${projectWebhooksApi(api.owner, api.projectCode)}/${projectWebhookId}`,
      { data: { ...projectWebhookPayload, enabled: false } },
    );
    expect(response.status()).toBe(200);
  });

  test('DELETE /api/{owner}/projects/{code}/settings/webhooks/{id}', async ({
    authedRequest,
    api,
  }) => {
    const response = await authedRequest.delete(
      `${projectWebhooksApi(api.owner, api.projectCode)}/${projectWebhookId}`,
    );
    expect(response.status()).toBe(204);
  });
});
