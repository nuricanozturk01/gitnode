import { repoApi } from '@helpers/paths';
import { seedReadmeOnMain } from '@helpers/seed-repo';
import type { E2eSession, RepoInfo } from '@helpers/types';
import type { APIRequestContext } from '@playwright/test';

import type { ScenarioRepo, ScenarioUser } from './types';

export function sessionOwner(session: E2eSession): ScenarioUser {
  return {
    username: session.username,
    email: session.email,
    password: session.password,
    token: session.accessToken,
    refreshToken: session.refreshToken,
    expiresIn: 0,
    refreshExpiresIn: 0,
    authorization: session.authorization,
  };
}

export function sessionIntruder(session: E2eSession): ScenarioUser {
  return {
    username: session.intruderUsername,
    email: session.intruderEmail,
    password: session.intruderPassword,
    token: session.intruderAccessToken,
    refreshToken: session.intruderRefreshToken,
    expiresIn: 0,
    refreshExpiresIn: 0,
    authorization: session.intruderAuthorization,
  };
}

export async function createScenarioRepo(
  request: APIRequestContext,
  user: ScenarioUser,
  options: { isPrivate: boolean; namePrefix?: string },
): Promise<ScenarioRepo> {
  const name = `${options.namePrefix ?? 'scn-repo'}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
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
  const response = await request.put(`/api/repos/${owner}/${repo}/blob/${branch}/${filePath}`, {
    headers: { Authorization: authorization },
    data: { content, commitMessage },
  });
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
  const response = await request.post(`/api/repos/${owner}/${repo}/pulls/${prNumber}/merge`, {
    headers: { Authorization: authorization },
    data: { strategy: 'MERGE_COMMIT', commitMessage: `Merge PR #${prNumber}` },
  });
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
  const response = await request.delete(`/api/repos/${owner}/${repo}/pulls/${prNumber}`, {
    headers: { Authorization: authorization },
  });
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

export async function getRepo(
  request: APIRequestContext,
  authorization: string | undefined,
  owner: string,
  repo: string,
): Promise<RepoInfo> {
  const response = await request.get(`${repoApi}/${owner}/${repo}`, {
    headers: authorization ? { Authorization: authorization } : undefined,
  });
  if (!response.ok()) {
    throw new Error(`get repo failed (${response.status()}): ${await response.text()}`);
  }
  return (await response.json()) as RepoInfo;
}

export async function listPullRequests(
  request: APIRequestContext,
  authorization: string | undefined,
  owner: string,
  repo: string,
  status = 'OPEN',
): Promise<{ content: { number: number; title: string }[] }> {
  const response = await request.get(`/api/repos/${owner}/${repo}/pulls`, {
    headers: authorization ? { Authorization: authorization } : undefined,
    params: { status, page: '0', size: '25' },
  });
  if (!response.ok()) {
    throw new Error(`list pulls failed (${response.status()}): ${await response.text()}`);
  }
  return (await response.json()) as { content: { number: number; title: string }[] };
}

export async function getPullRequest(
  request: APIRequestContext,
  authorization: string,
  owner: string,
  repo: string,
  prNumber: number,
): Promise<{ number: number; status: string; sourceBranch: string }> {
  const response = await request.get(`/api/repos/${owner}/${repo}/pulls/${prNumber}`, {
    headers: { Authorization: authorization },
  });
  if (!response.ok()) {
    throw new Error(`get pull failed (${response.status()}): ${await response.text()}`);
  }
  return (await response.json()) as { number: number; status: string; sourceBranch: string };
}

export async function listCommitMessages(
  request: APIRequestContext,
  authorization: string,
  owner: string,
  repo: string,
  branch: string,
): Promise<string[]> {
  const response = await request.get(`/api/repos/${owner}/${repo}/commits`, {
    headers: { Authorization: authorization },
    params: { branch, page: '0', size: '20' },
  });
  if (!response.ok()) {
    throw new Error(`list commits failed (${response.status()}): ${await response.text()}`);
  }
  const body = (await response.json()) as { items: { message: string }[] };
  return body.items.map((c) => c.message);
}

export async function setDefaultBranch(
  request: APIRequestContext,
  authorization: string,
  owner: string,
  repo: string,
  branchName: string,
): Promise<void> {
  const response = await request.patch(`/api/repos/${owner}/${repo}/branches/default`, {
    headers: { Authorization: authorization },
    data: { branchName },
  });
  if (!response.ok()) {
    throw new Error(`set default branch failed (${response.status()}): ${await response.text()}`);
  }
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
