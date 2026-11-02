import { expect, test } from '@playwright/test';

test('file comment conversations take replies', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: 'demo@example.org', avatarUrl: null };
  const author = { name: 'demo', email: 'demo@example.org', username: 'demo', avatarUrl: '' };
  const root = { id: 'root', path: 'src/notes', line: 1, parentId: null, body: 'Why?', commitSha: 'b'.repeat(40), outdated: false, author, canEdit: true, canDelete: true, createdAt: new Date().toISOString(), updatedAt: null };
  let comments: Record<string, unknown>[] = [root];
  let posted: Record<string, unknown> | undefined;
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/users/me') return route.fulfill({ json: user });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: { id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false } });
    if (path.startsWith('/api/repos/demo/project/blob/')) return route.fulfill({ json: { path: 'src/notes', name: 'notes', sha: '123', size: 12, content: Buffer.from('first\nsecond').toString('base64'), isBinary: false, language: 'plaintext', lineCount: 2 } });
    if (path === '/api/repos/demo/project/file-comments' && request.method() === 'GET') return route.fulfill({ json: { headSha: 'b'.repeat(40), comments } });
    if (path === '/api/repos/demo/project/file-comments' && request.method() === 'POST') {
      posted = request.postDataJSON();
      const created = { ...root, id: 'reply', parentId: posted?.parentId, body: posted?.body };
      comments = [...comments, created];
      return route.fulfill({ status: 201, json: created });
    }
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/blob/main/src/notes');
  await expect(page.getByText('Why?')).toBeVisible();
  await page.getByRole('button', { name: 'Reply', exact: true }).click();
  await page.getByLabel('Reply', { exact: true }).fill('Because.');
  await page.getByRole('button', { name: 'Reply', exact: true }).last().click();
  await expect(page.getByText('Because.')).toBeVisible();
  expect(posted).toMatchObject({ parentId: 'root', body: 'Because.', path: 'src/notes' });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
