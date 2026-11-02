import { expect, test } from '@playwright/test';

test('files can be attached to a release, downloaded and deleted', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  const release = { id: 'rel-1', tagName: 'v1', name: 'Version 1', body: null, isDraft: false, isPrerelease: false, isLatest: true, author: { username: 'demo', displayName: null }, publishedAt: new Date().toISOString(), createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
  let assets: Record<string, unknown>[] = [];
  let uploadedName = '';
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/users/me') return route.fulfill({ json: user });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: { id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false } });
    if (path === '/api/repos/demo/project/releases/tag/v1') return route.fulfill({ json: release });
    if (path === '/api/repos/demo/project/releases/rel-1/assets' && request.method() === 'GET') return route.fulfill({ json: assets });
    if (path === '/api/repos/demo/project/releases/rel-1/assets' && request.method() === 'POST') {
      uploadedName = /filename="([^"]+)"/.exec(request.postData() ?? '')?.[1] ?? '';
      const created = { id: 'a1', name: uploadedName, contentType: 'text/plain', size: 2048, sha256: 'ab'.repeat(32), downloadCount: 0, createdAt: new Date().toISOString() };
      assets = [created];
      return route.fulfill({ status: 201, json: created });
    }
    if (path === '/api/repos/demo/project/releases/rel-1/assets/a1' && request.method() === 'GET') return route.fulfill({ body: 'file-bytes', contentType: 'application/octet-stream' });
    if (path === '/api/repos/demo/project/releases/rel-1/assets/a1' && request.method() === 'DELETE') { assets = []; return route.fulfill({ status: 204 }); }
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/releases/tag/v1');
  await expect(page.getByText('No files attached.')).toBeVisible();

  await page.locator('#release-files').setInputFiles({ name: 'app.txt', mimeType: 'text/plain', buffer: Buffer.from('hi') });
  const list = page.getByRole('list', { name: 'Attached files' });
  await expect(list).toContainText('app.txt');
  await expect(list).toContainText('2.0 KB');
  expect(uploadedName).toBe('app.txt');

  const download = page.waitForEvent('download');
  await list.getByRole('button', { name: 'app.txt', exact: true }).click();
  expect((await download).suggestedFilename()).toBe('app.txt');
  await expect(list).toContainText('1 download');

  await list.getByRole('button', { name: 'Delete app.txt' }).click();
  await page.locator('app-confirm-modal-host .btn-error').click();
  await expect(page.getByText('No files attached.')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
