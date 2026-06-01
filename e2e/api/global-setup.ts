import { request as playwrightRequest } from '@playwright/test';

import { saveSession } from '@helpers/auth-store';
import { getApiBaseUrl } from '@helpers/env';
import { seedReadmeOnMain } from '@helpers/seed-repo';
import { E2E_PASSWORD, uniqueEmail, uniqueUsername } from '@helpers/test-user';
import type { E2eSession, LoginInfo, RepoInfo } from '@helpers/types';

async function globalSetup(): Promise<void> {
  const baseUrl = getApiBaseUrl();
  const username = uniqueUsername('e2e');
  const email = uniqueEmail(username);
  const password = E2E_PASSWORD;
  const runId = Date.now().toString(36);

  const request = await playwrightRequest.newContext({ baseURL: baseUrl });

  try {
    const registerResponse = await request.post('/api/auth/register', {
      data: { username, email, password },
    });
    if (!registerResponse.ok()) {
      const body = await registerResponse.text();
      throw new Error(
        `Global setup: register failed (${registerResponse.status()}): ${body}`,
      );
    }

    const loginInfo = (await registerResponse.json()) as LoginInfo;
    const authorization = `Bearer ${loginInfo.token}`;

    const repoName = `e2e-repo-${runId}`;
    const createRepoResponse = await request.post('/api/repo', {
      headers: { Authorization: authorization },
      data: { name: repoName, description: 'E2E shared fixture repo', isPrivate: false },
    });
    if (!createRepoResponse.ok()) {
      const body = await createRepoResponse.text();
      throw new Error(
        `Global setup: create repo failed (${createRepoResponse.status()}): ${body}`,
      );
    }
    const repo = (await createRepoResponse.json()) as RepoInfo;

    await seedReadmeOnMain(request, username, repo.name, authorization);

    const projectCode = `E2E${runId.slice(-4).toUpperCase()}`.slice(0, 10);
    const createProjectResponse = await request.post(
      `/api/projects/${username}`,
      {
        headers: { Authorization: authorization },
        data: {
          name: `E2E Project ${runId}`,
          description: 'Shared E2E project',
          codePrefix: projectCode,
          isPublic: true,
        },
      },
    );
    if (!createProjectResponse.ok()) {
      const body = await createProjectResponse.text();
      throw new Error(
        `Global setup: create project failed (${createProjectResponse.status()}): ${body}`,
      );
    }

    const session: E2eSession = {
      baseUrl,
      username,
      email,
      password,
      accessToken: loginInfo.token,
      refreshToken: loginInfo.refreshToken,
      authorization,
      repoName: repo.name,
      repoId: repo.id,
      projectCode,
    };

    saveSession(session);
    // eslint-disable-next-line no-console
    console.log(
      `[e2e] Registered ${username}, repo=${repoName}, project=${projectCode} @ ${baseUrl}`,
    );
  } finally {
    await request.dispose();
  }
}

export default globalSetup;
