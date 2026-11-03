import { expect, test } from '@playwright/test';

test('the edit page generates notes and manages files with progress and drag and drop', async ({
  page,
}) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  const release = {
    id: 'rel-1',
    tagName: 'v1',
    name: 'Version 1',
    body: null,
    isDraft: true,
    isPrerelease: false,
    isLatest: false,
    author: { username: 'demo', displayName: null },
    publishedAt: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
  let assets: Record<string, unknown>[] = [];
  let notesQuery: URLSearchParams | undefined;
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    if (path === '/api/users/me') return route.fulfill({ json: user });
    if (path === '/api/repo/demo/project')
      return route.fulfill({
        json: {
          id: 'repo-id',
          name: 'project',
          owner: user,
          defaultBranch: 'main',
          isPrivate: false,
        },
      });
    if (path === '/api/repos/demo/project/releases/rel-1') return route.fulfill({ json: release });
    if (path === '/api/repos/demo/project/releases/generate-notes') {
      notesQuery = url.searchParams;
      return route.fulfill({
        json: {
          name: 'v1',
          body: "## What's Changed\n\n* Add search\n",
          previousTag: null,
          commitCount: 1,
          truncated: false,
        },
      });
    }
    if (path === '/api/repos/demo/project/releases/rel-1/assets' && request.method() === 'GET')
      return route.fulfill({ json: assets });
    if (path === '/api/repos/demo/project/releases/rel-1/assets' && request.method() === 'POST') {
      const name = /filename="([^"]+)"/.exec(request.postData() ?? '')?.[1] ?? '';
      const created = {
        id: `a-${name}`,
        name,
        contentType: 'text/plain',
        size: 10,
        sha256: 'ab'.repeat(32),
        downloadCount: 0,
        createdAt: new Date().toISOString(),
      };
      assets = [...assets, created];
      return route.fulfill({ status: 201, json: created });
    }
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/releases/rel-1/edit');

  await page.getByRole('button', { name: 'Generate release notes' }).click();
  await expect(page.locator('#releaseBody')).toHaveValue(/What's Changed[\s\S]*Add search/);
  expect(notesQuery?.get('tagName')).toBe('v1');

  const list = page.getByRole('list', { name: 'Attached files' });
  await expect(list).toContainText('No files attached.');
  const dropzone = page.getByTestId('asset-dropzone');
  const data = await page.evaluateHandle(() => {
    const transfer = new DataTransfer();
    transfer.items.add(new File(['dropped!!!!'], 'dropped.txt', { type: 'text/plain' }));
    return transfer;
  });
  await dropzone.dispatchEvent('dragover', { dataTransfer: data });
  await dropzone.dispatchEvent('drop', { dataTransfer: data });
  await expect(list).toContainText('dropped.txt');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
});
