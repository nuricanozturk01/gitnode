import { getApiBaseUrl } from '@helpers/env';
import { authApi, repoApi, usersApi } from '@helpers/paths';
import { E2E_PASSWORD, uniqueEmail, uniqueUsername } from '@helpers/test-user';
import type { LoginInfo } from '@helpers/types';
import { expect, request as playwrightRequest, test } from '@playwright/test';

test.describe('Account deletion removes everything the account owns', () => {
  test('DELETE /api/users/me cascades to repositories, tokens and the account itself', async () => {
    const username = uniqueUsername('del');
    const email = uniqueEmail(username);
    const repoName = `del-repo-${Date.now().toString(36)}`;
    const ctx = await playwrightRequest.newContext({ baseURL: getApiBaseUrl() });
    try {
      const register = await ctx.post(`${authApi}/register`, {
        data: { username, email, password: E2E_PASSWORD },
      });
      expect(register.status()).toBe(200);
      const session = (await register.json()) as LoginInfo;
      const auth = { Authorization: `Bearer ${session.token}` };

      const created = await ctx.post(repoApi, {
        headers: auth,
        data: { name: repoName, description: 'to be deleted', isPrivate: false },
      });
      expect(created.status()).toBe(200);

      const visible = await ctx.get(`${repoApi}/${username}/${repoName}`, { headers: auth });
      expect(visible.status()).toBe(200);

      const deleted = await ctx.delete(`${usersApi}/me`, { headers: auth });
      expect(deleted.status()).toBe(204);

      // the repository is gone for everybody, including anonymous readers
      await expect
        .poll(async () => (await ctx.get(`${repoApi}/${username}/${repoName}`)).status(), {
          timeout: 10_000,
        })
        .toBe(404);

      // the account cannot sign in again and the old refresh token is dead
      const login = await ctx.post(`${authApi}/login`, {
        data: { usernameOrEmail: username, password: E2E_PASSWORD },
      });
      expect(login.status()).toBeGreaterThanOrEqual(400);
      const refresh = await ctx.post(`${authApi}/refresh-token`, {
        data: { refreshToken: session.refreshToken },
      });
      expect(refresh.status()).toBeGreaterThanOrEqual(400);

      // the user name is free again
      const again = await ctx.post(`${authApi}/register`, {
        data: { username, email, password: E2E_PASSWORD },
      });
      expect(again.status()).toBe(200);
      const second = (await again.json()) as LoginInfo;
      await ctx.delete(`${usersApi}/me`, {
        headers: { Authorization: `Bearer ${second.token}` },
      });
    } finally {
      await ctx.dispose();
    }
  });
});
