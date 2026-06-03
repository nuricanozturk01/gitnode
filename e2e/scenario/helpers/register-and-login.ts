import type { APIRequestContext } from '@playwright/test';

/**
 * Registers a new user (or falls back to login if the username already exists)
 * and returns a Bearer authorization header value.
 *
 * Used across scenario specs to create ephemeral collaborator accounts.
 */
export async function registerAndLogin(
  request: APIRequestContext,
  username: string,
): Promise<{ authorization: string }> {
  const email = `${username}@e2e.originhub.test`;
  const password = 'OriginHub1!';

  const registerResp = await request.post('/api/auth/register', {
    data: { username, email, password },
  });
  if (registerResp.ok()) {
    const body = await registerResp.json();
    return { authorization: `Bearer ${body.token}` };
  }

  const loginResp = await request.post('/api/auth/login', {
    data: { usernameOrEmail: username, password },
  });
  const body = await loginResp.json();
  return { authorization: `Bearer ${body.token}` };
}

/**
 * Deletes an ephemeral test user via DELETE /api/users/me using their own token.
 * Best-effort: silently ignores failures so teardown never blocks test results.
 */
export async function deleteUser(request: APIRequestContext, authorization: string): Promise<void> {
  try {
    await request.delete('/api/users/me', { headers: { Authorization: authorization } });
  } catch {
    // best-effort; ignore network errors in teardown
  }
}
