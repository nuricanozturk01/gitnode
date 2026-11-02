import { expect, test } from '@playwright/test';

test('the profile settings manage the time zone and extra commit e-mails', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', displayName: 'Demo', email: 'demo@example.org', avatarUrl: null, bio: null, website: null, location: null, profileReadme: null, timezone: null, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
  let emails: { id: string; email: string; createdAt: string }[] = [];
  let profileBody: Record<string, unknown> | undefined;
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/users/me' && request.method() === 'GET') return route.fulfill({ json: user });
    if (path === '/api/users/me/profile') {
      profileBody = request.postDataJSON();
      return route.fulfill({ json: { ...user, timezone: profileBody?.timezone } });
    }
    if (path === '/api/users/me/emails' && request.method() === 'GET') return route.fulfill({ json: emails });
    if (path === '/api/users/me/emails' && request.method() === 'POST') {
      const email = request.postDataJSON().email as string;
      if (email.includes('taken')) return route.fulfill({ status: 400, json: { message: 'This e-mail address belongs to another account' } });
      emails = [{ id: 'e1', email, createdAt: new Date().toISOString() }];
      return route.fulfill({ status: 201, json: emails[0] });
    }
    if (path === '/api/users/me/emails/e1') { emails = []; return route.fulfill({ status: 204 }); }
    return route.fulfill({ json: [] });
  });
  await page.goto('/settings');
  await expect(page.getByText('No extra e-mails.')).toBeVisible();
  await page.getByLabel('New e-mail address').fill('taken@example.net');
  await page.getByRole('button', { name: 'Add e-mail' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'another account' })).toBeVisible();
  await page.getByLabel('New e-mail address').fill('old@example.net');
  await page.getByRole('button', { name: 'Add e-mail' }).click();
  await expect(page.getByRole('list', { name: 'Extra e-mails' })).toContainText('old@example.net');
  await page.getByRole('button', { name: 'Remove old@example.net' }).click();
  await expect(page.getByText('No extra e-mails.')).toBeVisible();

  await page.getByLabel('Time zone').fill('Europe/Istanbul');
  await page.getByRole('button', { name: 'Save profile' }).click();
  await expect.poll(() => profileBody?.timezone).toBe('Europe/Istanbul');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
