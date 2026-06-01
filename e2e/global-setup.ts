import './helpers/load-env';

import fs from 'node:fs';

import { loadSession, saveSession, SESSION_FILE } from '@helpers/auth-store';
import { getApiBaseUrl } from '@helpers/env';
import { seedReadmeOnMain } from '@helpers/seed-repo';
import { E2E_PASSWORD, uniqueEmail, uniqueUsername } from '@helpers/test-user';
import type { E2eSession, LoginInfo } from '@helpers/types';
import { request as playwrightRequest } from '@playwright/test';

import {
  type ResolvedE2eUser,
  resolveIntruder,
  resolveOwner,
  shouldPreserveUsers,
} from './helpers/e2e-credentials';
import { refreshE2eSession } from './helpers/session-auth';
import { uniqueProjectCodePrefix } from './helpers/unique-id';

type RequestContext = Awaited<ReturnType<typeof playwrightRequest.newContext>>;

async function registerUser(request: RequestContext, username: string): Promise<ResolvedE2eUser> {
  const email = uniqueEmail(username);
  const password = E2E_PASSWORD;
  const response = await request.post('/api/auth/register', {
    data: { username, email, password },
  });
  if (!response.ok()) {
    throw new Error(
      `Global setup: register ${username} failed (${response.status()}): ${await response.text()}`,
    );
  }
  const login = (await response.json()) as LoginInfo;
  return { login, email, password, fromEnv: false };
}

async function globalSetup(): Promise<void> {
  const baseUrl = getApiBaseUrl();

  if (fs.existsSync(SESSION_FILE)) {
    try {
      const existing = loadSession();
      if (existing.baseUrl !== baseUrl) {
        fs.unlinkSync(SESSION_FILE);
        console.log(
          `[e2e] Removed stale session.json (was for ${existing.baseUrl}, now ${baseUrl})`,
        );
      }
    } catch {
      fs.unlinkSync(SESSION_FILE);
    }
  }

  const request = await playwrightRequest.newContext({ baseURL: baseUrl });

  if (process.env.E2E_TEARDOWN_ONLY === '1' && fs.existsSync(SESSION_FILE)) {
    console.log(`[e2e] Teardown-only: refreshing tokens for ${SESSION_FILE}`);
    await refreshE2eSession(request, loadSession());
    await request.dispose();
    return;
  }

  const runId = Date.now().toString(36);
  const generatedOwner = uniqueUsername('e2e');
  const generatedIntruder = uniqueUsername('e2eint');

  try {
    const owner = await resolveOwner(request, (u) => registerUser(request, u), generatedOwner);
    const intruder = await resolveIntruder(
      request,
      (u) => registerUser(request, u),
      generatedIntruder,
    );
    const username = owner.login.username;
    const authorization = `Bearer ${owner.login.token}`;

    const repoName = `e2e-repo-${runId}`;
    const createRepoResponse = await request.post('/api/repo', {
      headers: { Authorization: authorization },
      data: { name: repoName, description: 'E2E shared fixture repo', isPrivate: false },
    });
    if (!createRepoResponse.ok()) {
      const body = await createRepoResponse.text();
      throw new Error(`Global setup: create repo failed (${createRepoResponse.status()}): ${body}`);
    }
    const repo = (await createRepoResponse.json()) as { id: string; name: string };

    await seedReadmeOnMain(request, username, repo.name, authorization);

    const projectCode = uniqueProjectCodePrefix();
    const createProjectResponse = await request.post(`/api/projects/${username}`, {
      headers: { Authorization: authorization },
      data: {
        name: `E2E project ${runId}-${Math.random().toString(36).slice(2, 8)}`,
        description: 'Shared E2E project',
        codePrefix: projectCode,
        isPublic: true,
      },
    });
    if (!createProjectResponse.ok()) {
      const body = await createProjectResponse.text();
      throw new Error(
        `Global setup: create project failed (${createProjectResponse.status()}): ${body}`,
      );
    }

    const session: E2eSession = {
      baseUrl,
      username,
      email: owner.email,
      password: owner.password,
      accessToken: owner.login.token,
      refreshToken: owner.login.refreshToken,
      authorization,
      repoName: repo.name,
      repoId: repo.id,
      projectCode,
      intruderUsername: intruder.login.username,
      intruderEmail: intruder.email,
      intruderPassword: intruder.password,
      intruderAccessToken: intruder.login.token,
      intruderRefreshToken: intruder.login.refreshToken,
      intruderAuthorization: `Bearer ${intruder.login.token}`,
      preserveUsers: shouldPreserveUsers(),
    };

    saveSession(session);

    const mode = owner.fromEnv || intruder.fromEnv ? 'env credentials' : 'auto-registered';
    console.log(
      `[e2e] Session (${mode}): owner=${username}, intruder=${intruder.login.username}, repo=${repoName}, project=${projectCode} @ ${baseUrl}`,
    );
    if (session.preserveUsers) {
      console.log('[e2e] preserveUsers=true — teardown will not DELETE /api/users/me');
    }
  } finally {
    await request.dispose();
  }
}

export default globalSetup;
