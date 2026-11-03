import { expect, test } from '@playwright/test';

test('comments on a file and on a line can be written, edited and deleted', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = {
    id: 'user-id',
    username: 'demo',
    name: 'Demo',
    email: 'demo@example.org',
    avatarUrl: null,
  };
  const author = { name: 'demo', email: 'demo@example.org', username: 'demo', avatarUrl: '' };
  const old = {
    id: 'old',
    path: 'src/notes',
    line: 1,
    body: 'Old remark',
    commitSha: 'a'.repeat(40),
    outdated: true,
    author,
    canEdit: false,
    canDelete: false,
    createdAt: new Date().toISOString(),
    updatedAt: null,
  };
  let comments: Record<string, unknown>[] = [old];
  const posted: unknown[] = [];
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
    if (path === '/api/repos/demo/project/blob/main/src/notes') {
      return route.fulfill({
        json: {
          path: 'src/notes',
          name: 'notes',
          sha: '123',
          size: 12,
          content: Buffer.from('first\nsecond\nthird').toString('base64'),
          isBinary: false,
          language: 'plaintext',
          lineCount: 3,
        },
      });
    }
    if (path === '/api/repos/demo/project/file-comments' && request.method() === 'GET')
      return route.fulfill({ json: { headSha: 'b'.repeat(40), comments } });
    if (path === '/api/repos/demo/project/file-comments' && request.method() === 'POST') {
      const body = request.postDataJSON();
      posted.push(body);
      const created = {
        id: `c${posted.length}`,
        path: body.path,
        line: body.line ?? null,
        body: body.body,
        commitSha: 'b'.repeat(40),
        outdated: false,
        author,
        canEdit: true,
        canDelete: true,
        createdAt: new Date().toISOString(),
        updatedAt: null,
      };
      comments = [...comments, created];
      return route.fulfill({ status: 201, json: created });
    }
    if (
      path.startsWith('/api/repos/demo/project/file-comments/c') &&
      request.method() === 'PATCH'
    ) {
      const updated: Record<string, unknown> = {
        ...comments.find((c) => c.id === path.split('/').pop()),
        body: request.postDataJSON().body,
        updatedAt: new Date().toISOString(),
      };
      comments = comments.map((c) => (c.id === updated.id ? updated : c));
      return route.fulfill({ json: updated });
    }
    if (
      path.startsWith('/api/repos/demo/project/file-comments/c') &&
      request.method() === 'DELETE'
    ) {
      comments = comments.filter((c) => c.id !== path.split('/').pop());
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ status: 404 });
  });

  await page.goto('/demo/project/blob/main/src/notes');
  await expect(page.getByText('No comments on this file yet.')).toHaveCount(0);
  await expect(page.getByText('Old remark')).toHaveCount(0);
  await page.getByRole('button', { name: 'Show 1 outdated' }).click();
  await expect(page.getByText('Old remark')).toBeVisible();
  await expect(page.getByText(/Outdated · aaaaaaa/)).toBeVisible();

  await page.getByLabel('Comment on this file', { exact: true }).fill('General remark');
  await page.getByRole('button', { name: 'Comment', exact: true }).click();
  await expect(page.getByText('General remark')).toBeVisible();

  await page.getByRole('button', { name: 'Comment on line 2' }).click();
  await expect(page.getByText('Comment on line 2', { exact: true })).toBeVisible();
  await page.locator('#fc-new').fill('About second');
  await page.getByRole('button', { name: 'Comment', exact: true }).click();
  await expect(page.getByText('Line 2', { exact: true })).toBeVisible();
  await expect(page.getByLabel('Has comments')).toHaveCount(1);
  expect(posted).toEqual([
    { branch: 'main', path: 'src/notes', line: null, body: 'General remark' },
    { branch: 'main', path: 'src/notes', line: 2, body: 'About second' },
  ]);

  const general = page.locator('.fc-item', { hasText: 'General remark' });
  await general.getByRole('button', { name: 'Edit' }).click();
  await page.getByLabel('Edit comment').fill('General remark (clarified)');
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('General remark (clarified)')).toBeVisible();
  await expect(page.getByText('(edited)')).toBeVisible();

  await page
    .locator('.fc-item', { hasText: 'General remark (clarified)' })
    .getByRole('button', { name: 'Delete' })
    .click();
  await page.locator('.btn-error', { hasText: 'Delete' }).click();
  await expect(page.getByText('General remark (clarified)')).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
});
