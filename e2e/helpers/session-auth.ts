import { loadSession, saveSession } from '@helpers/auth-store';
import { getApiBaseUrl } from '@helpers/env';
import type { E2eSession, LoginInfo } from '@helpers/types';
import type { APIRequestContext } from '@playwright/test';

import { loginUser } from './e2e-credentials';

async function refreshAccessToken(
  request: APIRequestContext,
  refreshToken: string,
): Promise<LoginInfo> {
  const response = await request.post('/api/auth/refresh-token', {
    data: { refreshToken },
  });
  if (!response.ok()) {
    throw new Error(`refresh-token failed (${response.status()}): ${await response.text()}`);
  }
  return (await response.json()) as LoginInfo;
}

async function accessTokenIsValid(
  request: APIRequestContext,
  authorization: string,
): Promise<boolean> {
  const response = await request.get('/api/users/me', {
    headers: { Authorization: authorization },
  });
  return response.ok();
}

function applyOwnerLogin(session: E2eSession, login: LoginInfo): E2eSession {
  return {
    ...session,
    username: login.username,
    email: login.email,
    accessToken: login.token,
    refreshToken: login.refreshToken,
    authorization: `Bearer ${login.token}`,
  };
}

function applyIntruderLogin(session: E2eSession, login: LoginInfo): E2eSession {
  return {
    ...session,
    intruderUsername: login.username,
    intruderEmail: login.email,
    intruderAccessToken: login.token,
    intruderRefreshToken: login.refreshToken,
    intruderAuthorization: `Bearer ${login.token}`,
  };
}

async function renewOwner(session: E2eSession, request: APIRequestContext): Promise<LoginInfo> {
  try {
    return await refreshAccessToken(request, session.refreshToken);
  } catch {
    return loginUser(request, session.username, session.password);
  }
}

async function renewIntruder(session: E2eSession, request: APIRequestContext): Promise<LoginInfo> {
  try {
    return await refreshAccessToken(request, session.intruderRefreshToken);
  } catch {
    return loginUser(request, session.intruderUsername, session.intruderPassword);
  }
}

/** Refresh access tokens (or re-login) and persist session.json. */
export async function refreshE2eSession(
  request: APIRequestContext,
  session: E2eSession,
): Promise<E2eSession> {
  const ownerLogin = await renewOwner(session, request);
  const intruderLogin = await renewIntruder(session, request);
  const updated = applyIntruderLogin(applyOwnerLogin(session, ownerLogin), intruderLogin);
  saveSession(updated);
  return updated;
}

/**
 * Load session; refresh when tokens are invalid for the current API base URL.
 * Call once per worker (scenario) or at global-setup (teardown-only).
 */
export async function loadValidSession(request: APIRequestContext): Promise<E2eSession> {
  let session = loadSession();
  const baseUrl = getApiBaseUrl();

  if (session.baseUrl !== baseUrl) {
    throw new Error(
      `E2E session was created for ${session.baseUrl} but tests target ${baseUrl}. ` +
        'Delete e2e/.auth/session.json and re-run global setup.',
    );
  }

  const ownerOk = await accessTokenIsValid(request, session.authorization);
  const intruderOk = await accessTokenIsValid(request, session.intruderAuthorization);

  if (!ownerOk || !intruderOk) {
    console.log('[e2e] Access token invalid or expired — refreshing session');
    session = await refreshE2eSession(request, session);
  }

  return session;
}
