import { projectWebhooksApi, repoWebhooksApi } from '@helpers/paths';
import type { APIRequestContext } from '@playwright/test';

import type { ScenarioUser } from './types';

export interface WebhookInfoResponse {
  id: string;
  url: string;
  enabled: boolean;
  events: string[];
}

export async function createRepoWebhook(
  request: APIRequestContext,
  user: ScenarioUser,
  owner: string,
  repoName: string,
  payload: { url: string; secret: string; enabled: boolean; events: string[] },
): Promise<WebhookInfoResponse> {
  const response = await request.post(repoWebhooksApi(owner, repoName), {
    headers: { Authorization: user.authorization },
    data: payload,
  });
  if (!response.ok()) {
    throw new Error(`create repo webhook failed (${response.status()}): ${await response.text()}`);
  }
  return (await response.json()) as WebhookInfoResponse;
}

export async function listRepoWebhooks(
  request: APIRequestContext,
  user: ScenarioUser,
  owner: string,
  repoName: string,
): Promise<WebhookInfoResponse[]> {
  const response = await request.get(repoWebhooksApi(owner, repoName), {
    headers: { Authorization: user.authorization },
  });
  if (!response.ok()) {
    throw new Error(`list repo webhooks failed (${response.status()}): ${await response.text()}`);
  }
  return (await response.json()) as WebhookInfoResponse[];
}

export async function createProjectWebhook(
  request: APIRequestContext,
  user: ScenarioUser,
  owner: string,
  projectCode: string,
  payload: { url: string; secret: string; enabled: boolean; events: string[] },
): Promise<WebhookInfoResponse> {
  const response = await request.post(projectWebhooksApi(owner, projectCode), {
    headers: { Authorization: user.authorization },
    data: payload,
  });
  if (!response.ok()) {
    throw new Error(
      `create project webhook failed (${response.status()}): ${await response.text()}`,
    );
  }
  return (await response.json()) as WebhookInfoResponse;
}

export async function deleteRepoWebhook(
  request: APIRequestContext,
  user: ScenarioUser,
  owner: string,
  repoName: string,
  webhookId: string,
): Promise<void> {
  const response = await request.delete(`${repoWebhooksApi(owner, repoName)}/${webhookId}`, {
    headers: { Authorization: user.authorization },
  });
  if (!response.ok()) {
    throw new Error(`delete repo webhook failed (${response.status()}): ${await response.text()}`);
  }
}
