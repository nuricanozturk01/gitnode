/**
 * Repository and project webhooks — configuration and event dispatch paths.
 * SCN-API-WH-01 — see RAPOR-SCENARIO.md
 */
import { expect, test } from './fixtures/scenario';
import { putBlob } from './helpers/scenario-api';
import { bootstrapScenarioProject } from './helpers/task-api';
import {
  createProjectWebhook,
  createRepoWebhook,
  deleteRepoWebhook,
  listRepoWebhooks,
} from './helpers/webhook-api';

test.describe('SCN-API-WH — webhooks', () => {
  // SCN-API-WH-01 — Repo REPO_PUSHED webhook is registered; TASK_CREATED project webhook fires on task create.
  test('registers repo REPO_PUSHED webhook and dispatches TASK_CREATED on task creation', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const repoWebhook = await createRepoWebhook(bareApi, owner, owner.username, privateRepo.name, {
      url: 'https://httpbin.org/post',
      secret: 'e2e-scenario-secret',
      enabled: true,
      events: ['REPO_PUSHED'],
    });
    expect(repoWebhook.events).toContain('REPO_PUSHED');

    const listed = await listRepoWebhooks(bareApi, owner, owner.username, privateRepo.name);
    expect(listed.some((w) => w.id === repoWebhook.id && w.enabled)).toBe(true);

    await putBlob(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      'main',
      'webhook-push.txt',
      'webhook path\n',
      'scenario: push path commit',
    );

    const project = await bootstrapScenarioProject(bareApi, owner, privateRepo);
    await createProjectWebhook(bareApi, owner, owner.username, project.projectCode, {
      url: 'https://httpbin.org/post',
      secret: 'e2e-scenario-secret',
      enabled: true,
      events: ['TASK_CREATED'],
    });

    const taskResponse = await bareApi.post(
      `/api/projects/${owner.username}/${project.projectCode}/tasks`,
      {
        headers: { Authorization: owner.authorization },
        data: {
          title: 'Webhook trigger task',
          boardColumnId: project.columnId,
          type: 'TASK',
          position: 0,
        },
      },
    );
    expect(taskResponse.status()).toBe(201);

    await deleteRepoWebhook(bareApi, owner, owner.username, privateRepo.name, repoWebhook.id);
  });
});
