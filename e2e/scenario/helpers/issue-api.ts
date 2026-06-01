import { repoIssuesApi } from '@helpers/paths';
import type { APIRequestContext } from '@playwright/test';

import type { ScenarioUser } from './types';

export async function createScenarioIssue(
  request: APIRequestContext,
  user: ScenarioUser,
  owner: string,
  repoName: string,
  title: string,
): Promise<{ number: number; id: string }> {
  const response = await request.post(repoIssuesApi(owner, repoName), {
    headers: { Authorization: user.authorization },
    data: { title, description: 'Scenario issue' },
  });
  if (!response.ok()) {
    throw new Error(`create issue failed (${response.status()}): ${await response.text()}`);
  }

  return (await response.json()) as { number: number; id: string };
}

export async function getLinkedTasksForIssue(
  request: APIRequestContext,
  authorization: string,
  owner: string,
  repoName: string,
  issueNumber: number,
): Promise<{ taskCode: string; taskTitle: string }[]> {
  const response = await request.get(
    `${repoIssuesApi(owner, repoName)}/${issueNumber}/linked-tasks`,
    { headers: { Authorization: authorization } },
  );
  if (!response.ok()) {
    throw new Error(`get linked tasks failed (${response.status()}): ${await response.text()}`);
  }
  return (await response.json()) as { taskCode: string; taskTitle: string }[];
}
