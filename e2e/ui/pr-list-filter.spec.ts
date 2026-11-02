import { expect, test } from '@playwright/test';

test('the pull request list shows labels, milestone and assignees and filters by them', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  const author = { name: 'Demo', email: '', username: 'demo', avatarUrl: '' };
  const pr = (number: number, extra: object) => ({ id: `pr-${number}`, number, title: `PR ${number}`, status: 'OPEN', isDraft: false, author, mergedBy: null, sourceBranch: `f${number}`, targetBranch: 'main', mergeSha: null, commentCount: 0, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), mergedAt: null, closedAt: null, labels: [], assignees: [], milestone: null, ...extra });
  const rows = [pr(1, { labels: [{ id: 'l1', name: 'bug', color: 'd73a4a', description: null }], assignees: [author], milestone: { id: 'm1', title: 'v1', status: 'OPEN' } }), pr(2, {})];
  const queries: string[] = [];
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/users/me') return route.fulfill({ json: user });
    if (url.pathname === '/api/repo/demo/project') return route.fulfill({ json: { id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false } });
    if (url.pathname === '/api/repos/demo/project/labels') return route.fulfill({ json: [{ id: 'l1', name: 'bug', color: 'd73a4a', description: null }] });
    if (url.pathname === '/api/repos/demo/project/milestones') return route.fulfill({ json: [{ id: 'm1', title: 'v1', description: '', status: 'OPEN', openIssues: 0, closedIssues: 0 }] });
    if (url.pathname === '/api/repos/demo/project/pulls') {
      queries.push(url.search);
      const filtered = url.searchParams.get('labelId') ? [rows[0]] : rows;
      return route.fulfill({ json: { content: filtered, number: 0, size: 25, totalElements: filtered.length, totalPages: 1 } });
    }
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/pulls');
  await expect(page.getByText('PR 2')).toBeVisible();
  const first = page.getByRole('link', { name: /PR 1/ });
  await expect(first).toContainText('bug');
  await expect(first).toContainText('v1');
  await expect(first).toContainText('@demo');

  await page.getByLabel('Filter by label').selectOption('l1');
  await expect.poll(() => queries.at(-1) ?? '').toContain('labelId=l1');
  await expect(page.getByText('PR 1')).toBeVisible();
  await expect(page.getByText('PR 2')).toHaveCount(0);
  await page.getByRole('button', { name: 'Clear filters' }).click();
  await expect(page.getByText('PR 2')).toBeVisible();
});
