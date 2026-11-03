import { expect, test } from '@playwright/test';

test('a closed pull request can be reopened', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  let status = 'CLOSED';
  let reopened = 0;
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
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
    if (path === '/api/repos/demo/project/pulls/1')
      return route.fulfill({
        json: {
          id: 'pr-id',
          number: 1,
          title: 'Done',
          status: status,
          author: user,
          mergedBy: null,
          sourceBranch: 'feature',
          targetBranch: 'main',
          isDraft: false,
          description: null,
          createdAt: null,
        },
      });
    if (path === '/api/repos/demo/project/pulls/1/reopen') {
      reopened += 1;
      status = 'OPEN';
      return route.fulfill({
        json: {
          id: 'pr-id',
          number: 1,
          title: 'Done',
          status,
          author: user,
          mergedBy: null,
          sourceBranch: 'feature',
          targetBranch: 'main',
          isDraft: false,
          description: null,
          createdAt: null,
        },
      });
    }
    if (path.endsWith('/merge-requirements'))
      return route.fulfill({
        json: {
          protectedBranch: false,
          requiredApprovals: 0,
          currentApprovals: 0,
          changesRequestedBy: [],
          checks: [],
          satisfied: true,
        },
      });
    if (path.endsWith('/conflicts'))
      return route.fulfill({
        json: { mergeable: true, sourceSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), files: [] },
      });
    if (path === '/api/repos/demo/project/branches')
      return route.fulfill({ json: [{ name: 'main', isDefault: true }] });
    if (path.endsWith('/reviews'))
      return route.fulfill({
        json: { headSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), reviews: [] },
      });
    if (path.endsWith('/reviewers')) return route.fulfill({ json: [] });
    if (path.endsWith('/comments'))
      return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/pulls/1');
  await page.getByRole('button', { name: 'Reopen pull request' }).click();
  await expect(page.getByRole('button', { name: 'Reopen pull request' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Merge', exact: true }).first()).toBeVisible();
  expect(reopened).toBe(1);
});
