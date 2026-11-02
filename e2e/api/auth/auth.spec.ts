import { loadSession } from '@helpers/auth-store';
import { getApiBaseUrl } from '@helpers/env';
import { authApi, usersApi } from '@helpers/paths';
import { E2E_PASSWORD, uniqueEmail, uniqueUsername } from '@helpers/test-user';
import type { LoginInfo } from '@helpers/types';
import { expect, request as playwrightRequest, test } from '@playwright/test';

test.describe('Auth API — all endpoints', () => {
  test('POST /api/auth/register', async () => {
    const username = uniqueUsername('reg');
    const email = uniqueEmail(username);
    const ctx = await playwrightRequest.newContext({ baseURL: getApiBaseUrl() });
    try {
      const response = await ctx.post(`${authApi}/register`, {
        data: { username, email, password: E2E_PASSWORD },
      });
      expect(response.status()).toBe(200);
      const body = (await response.json()) as LoginInfo;
      expect(body.username).toBe(username);
      expect(body.token).toBeTruthy();
      expect(body.refreshToken).toBeTruthy();

      const deleteResponse = await ctx.delete(`${usersApi}/me`, {
        headers: { Authorization: `Bearer ${body.token}` },
      });
      expect(deleteResponse.status()).toBe(204);
    } finally {
      await ctx.dispose();
    }
  });

  test('POST /api/auth/login', async () => {
    const session = loadSession();
    const ctx = await playwrightRequest.newContext({ baseURL: session.baseUrl });
    try {
      const response = await ctx.post(`${authApi}/login`, {
        data: { usernameOrEmail: session.email, password: session.password },
      });
      expect(response.status()).toBe(200);
      const body = (await response.json()) as LoginInfo;
      expect(body.username).toBe(session.username);
    } finally {
      await ctx.dispose();
    }
  });

  test('POST /api/auth/refresh-token', async () => {
    const session = loadSession();
    const ctx = await playwrightRequest.newContext({ baseURL: session.baseUrl });
    try {
      // Sign in again: other tests change the shared user's password, which ends older sessions.
      const login = await ctx.post(`${authApi}/login`, {
        data: { usernameOrEmail: session.username, password: session.password },
      });
      expect(login.status()).toBe(200);
      const fresh = (await login.json()) as LoginInfo;
      const response = await ctx.post(`${authApi}/refresh-token`, {
        data: { refreshToken: fresh.refreshToken },
      });
      expect(response.status()).toBe(200);
      const body = (await response.json()) as LoginInfo;
      expect(body.token).toBeTruthy();
      expect(body.refreshToken).not.toBe(fresh.refreshToken);
    } finally {
      await ctx.dispose();
    }
  });

  test('POST /api/auth/send-password-recovery-mail', async () => {
    const session = loadSession();
    const ctx = await playwrightRequest.newContext({ baseURL: session.baseUrl });
    try {
      const response = await ctx.post(`${authApi}/send-password-recovery-mail`, {
        data: { usernameOrEmail: session.email },
      });
      expect(response.status()).toBe(200);
      const unknown = await ctx.post(`${authApi}/send-password-recovery-mail`, {
        data: { usernameOrEmail: 'nobody-here@example.org' },
      });
      expect(unknown.status()).toBe(200);
      expect(await unknown.text()).toBe(await response.text());
    } finally {
      await ctx.dispose();
    }
  });

  test('POST /api/auth/recover-password rejects unknown code', async () => {
    const session = loadSession();
    const ctx = await playwrightRequest.newContext({ baseURL: session.baseUrl });
    try {
      const response = await ctx.post(`${authApi}/recover-password`, {
        data: {
          usernameOrEmail: session.email,
          recoveryCode: '000000',
          newPassword: 'NewPass1!x',
        },
      });
      expect(response.status()).toBeGreaterThanOrEqual(400);
    } finally {
      await ctx.dispose();
    }
  });
});
