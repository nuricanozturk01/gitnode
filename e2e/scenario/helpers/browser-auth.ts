import type { Page } from '@playwright/test';

import type { ScenarioUser } from './types';

const ACCESS_KEY = 'token';
const REFRESH_KEY = 'refresh_token';
const EXPIRES_KEY = 'expires_at';
const REFRESH_EXPIRES_KEY = 'refresh_expires_at';
const USERNAME_KEY = 'username';

/** Seeds OriginHub SPA session without going through the login form. */
export async function seedBrowserSession(page: Page, user: ScenarioUser): Promise<void> {
  const expiresAt = Date.now() + 30 * 60 * 1000;
  const refreshExpiresAt = Date.now() + 60 * 60 * 1000;
  await page.addInitScript(
    ({ token, refreshToken, username, expiresAtMs, refreshExpiresAtMs }) => {
      localStorage.setItem('token', token);
      localStorage.setItem('refresh_token', refreshToken);
      localStorage.setItem('expires_at', String(expiresAtMs));
      localStorage.setItem('refresh_expires_at', String(refreshExpiresAtMs));
      localStorage.setItem('username', username);
    },
    {
      token: user.token,
      refreshToken: user.refreshToken,
      username: user.username,
      expiresAtMs: expiresAt,
      refreshExpiresAtMs: refreshExpiresAt,
    },
  );
}

export async function loginViaForm(
  page: Page,
  user: ScenarioUser,
  webBaseUrl: string,
): Promise<void> {
  await page.goto(`${webBaseUrl}/login`);
  await page.locator('#usernameOrEmail').fill(user.username);
  await page.locator('#password').fill(user.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.waitForURL(/\/(dashboard|profile)/);
}
