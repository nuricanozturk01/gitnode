import { expect, test } from '@playwright/test';

test('confirmation dialogs move focus inside, trap Tab, close on Escape and return focus', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  const release = { id: 'rel-1', tagName: 'v1', name: 'Version 1', body: null, isDraft: false, isPrerelease: false, isLatest: true, author: { username: 'demo', displayName: null }, publishedAt: new Date().toISOString(), createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
  const asset = { id: 'a1', name: 'app.txt', contentType: 'text/plain', size: 10, sha256: 'ab'.repeat(32), downloadCount: 0, createdAt: new Date().toISOString() };
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/users/me') return route.fulfill({ json: user });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: { id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false } });
    if (path === '/api/repos/demo/project/releases/tag/v1') return route.fulfill({ json: release });
    if (path === '/api/repos/demo/project/releases/rel-1/assets') return route.fulfill({ json: [asset] });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/releases/tag/v1');
  const trigger = page.getByRole('button', { name: 'Delete app.txt' });
  await trigger.focus();
  await trigger.click();

  const dialog = page.getByRole('alertdialog', { name: 'Delete file' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Cancel' })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(dialog.getByRole('button', { name: 'Delete' })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(dialog.getByRole('button', { name: 'Cancel' })).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  await expect(dialog.getByRole('button', { name: 'Delete' })).toBeFocused();

  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
});
