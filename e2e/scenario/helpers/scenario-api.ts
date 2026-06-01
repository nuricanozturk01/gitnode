import type { APIRequestContext } from '@playwright/test';

import { repoApi } from '@helpers/paths';
import { seedReadmeOnMain } from '@helpers/seed-repo';
import { E2E_PASSWORD, uniqueEmail, uniqueUsername } from '@helpers/test-user';
import type { LoginInfo, RepoInfo } from '@helpers/types';

import type { ScenarioRepo, ScenarioUser } from './types';

export async function registerScenarioUser(
  request: APIRequestContext,
): Promise<ScenarioUser> {
  const username = uniqueUsername('scn');
  const email = uniqueEmail(username);
  const password = E2E_PASSWORD;
  const response = await request.post('/api/auth/register', {
    data: { username, email, password },
  });
  if (!response.ok()) {
    throw new Error(`register failed (${response.status()}): ${await response.text()}`);
  }
  const login = (await response.json()) as LoginInfo;
  return {
    ...login,
    email,
    password,
    authorization: `Bearer ${login.token}`,
  };
}

export async function createScenarioRepo(
  request: APIRequestContext,
  user: ScenarioUser,
  options: { isPrivate: boolean; namePrefix?: string },
): Promise<ScenarioRepo> {
  const name = `${options.namePrefix ?? 'scn-repo'}-${Date.now().toString(36)}`;
  const response = await request.post(repoApi, {
    headers: { Authorization: user.authorization },
    data: { name, description: 'Scenario E2E repo', isPrivate: options.isPrivate },
  });
  if (!response.ok()) {
    throw new Error(`create repo failed (${response.status()}): ${await response.text()}`);
  }
  const repo = (await response.json()) as RepoInfo;
  await seedReadmeOnMain(request, user.username, repo.name, user.authorization);
  return {
    name: repo.name,
    id: repo.id,
    isPrivate: options.isPrivate,
    defaultBranch: repo.defaultBranch ?? 'main',
  };
}

export async function patchRepoSettings(
  request: APIRequestContext,
  user: ScenarioUser,
  owner: string,
  repo: string,
  body: Record<string, unknown>,
): Promise<RepoInfo> {
  const response = await request.patch(`${repoApi}/${owner}/${repo}`, {
    headers: { Authorization: user.authorization },
    data: body,
  });
  if (!response.ok()) {
    throw new Error(`patch repo failed (${response.status()}): ${await response.text()}`);
  }
  return (await response.json()) as RepoInfo;
}

export async function createBranch(
  request: APIRequestContext,
  authorization: string,
  owner: string,
  repo: string,
  branchName: string,
  sourceBranch = 'main',
): Promise<void> {
  const response = await request.post(`/api/repos/${owner}/${repo}/branches`, {
    headers: { Authorization: authorization },
    data: { name: branchName, sourceBranch },
  });
  if (!response.ok()) {
    throw new Error(`create branch failed (${response.status()}): ${await response.text()}`);
  }
}

export async function putBlob(
  request: APIRequestContext,
  authorization: string,
  owner: string,
  repo: string,
  branch: string,
  filePath: string,
  content: string,
  commitMessage: string,
): Promise<void> {
  const response = await request.put(
    `/api/repos/${owner}/${repo}/blob/${branch}/${filePath}`,
    {
      headers: { Authorization: authorization },
      data: { content, commitMessage },
    },
  );
  if (!response.ok()) {
    throw new Error(`put blob failed (${response.status()}): ${await response.text()}`);
  }
}

export async function createPullRequest(
  request: APIRequestContext,
  authorization: string,
  owner: string,
  repo: string,
  data: {
    title: string;
    sourceBranch: string;
    targetBranch?: string;
    isDraft?: boolean;
  },
): Promise<{ number: number }> {
  const response = await request.post(`/api/repos/${owner}/${repo}/pulls`, {
    headers: { Authorization: authorization },
    data: {
      title: data.title,
      description: 'Scenario E2E',
      sourceBranch: data.sourceBranch,
      targetBranch: data.targetBranch ?? 'main',
      isDraft: data.isDraft ?? false,
    },
  });
  if (!response.ok()) {
    throw new Error(`create PR failed (${response.status()}): ${await response.text()}`);
  }
  return (await response.json()) as { number: number };
}

export async function mergePullRequest(
  request: APIRequestContext,
  authorization: string,
  owner: string,
  repo: string,
  prNumber: number,
): Promise<void> {
  const response = await request.post(
    `/api/repos/${owner}/${repo}/pulls/${prNumber}/merge`,
    {
      headers: { Authorization: authorization },
      data: { strategy: 'MERGE_COMMIT', commitMessage: `Merge PR #${prNumber}` },
    },
  );
  if (!response.ok()) {
    throw new Error(`merge PR failed (${response.status()}): ${await response.text()}`);
  }
}

export async function closePullRequest(
  request: APIRequestContext,
  authorization: string,
  owner: string,
  repo: string,
  prNumber: number,
): Promise<void> {
  const response = await request.delete(
    `/api/repos/${owner}/${repo}/pulls/${prNumber}`,
    { headers: { Authorization: authorization } },
  );
  if (!response.ok()) {
    throw new Error(`close PR failed (${response.status()}): ${await response.text()}`);
  }
}

export async function branchExists(
  request: APIRequestContext,
  authorization: string,
  owner: string,
  repo: string,
  branch: string,
): Promise<boolean> {
  const response = await request.get(`/api/repos/${owner}/${repo}/branches/${branch}`, {
    headers: { Authorization: authorization },
  });
  return response.ok();
}

export async function prepareFeatureBranch(
  request: APIRequestContext,
  authorization: string,
  owner: string,
  repo: string,
  branchName: string,
): Promise<void> {
  await createBranch(request, authorization, owner, repo, branchName);
  await putBlob(
    request,
    authorization,
    owner,
    repo,
    branchName,
    `scenario/${branchName}.txt`,
    `content for ${branchName}\n`,
    `scenario: ${branchName}`,
  );
}
