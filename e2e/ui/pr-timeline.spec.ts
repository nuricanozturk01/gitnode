import { expect, test } from '@playwright/test';

test('the conversation shows commits, reviews, workflow runs and the merge in order', async ({
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
  const t = (minutes: number) => new Date(Date.UTC(2026, 10, 1, 10, minutes)).toISOString();
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
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
          title: 'Update',
          status: 'MERGED',
          author: user,
          mergedBy: user,
          sourceBranch: 'feature',
          targetBranch: 'main',
          isDraft: false,
          description: null,
          createdAt: t(0),
          mergedAt: t(40),
          closedAt: null,
        },
      });
    if (path === '/api/repos/demo/project/pulls/1/commits')
      return route.fulfill({
        json: [
          {
            sha: 'a'.repeat(40),
            shortSha: 'aaaaaaa',
            message: 'Add feature',
            author: { name: 'Demo', username: 'demo', email: '', avatarUrl: '' },
            committedAt: t(5),
          },
        ],
      });
    if (path === '/api/repos/demo/project/pulls/1/reviews')
      return route.fulfill({
        json: {
          headSha: 'a'.repeat(40),
          targetSha: 'b'.repeat(40),
          reviews: [
            {
              id: 'r1',
              username: 'bob',
              state: 'APPROVED',
              body: null,
              commitSha: 'a'.repeat(40),
              targetSha: null,
              createdAt: t(30),
            },
          ],
        },
      });
    if (path === '/api/repos/demo/project/actions/runs')
      return route.fulfill({
        json: {
          content: [
            {
              id: 'run1',
              runNumber: 7,
              workflowName: 'ci',
              status: 'SUCCESS',
              conclusion: 'success',
              triggerEvent: 'pull_request',
              triggerRef: 'refs/heads/feature',
              triggerSha: 'a'.repeat(40),
              startedAt: null,
              completedAt: null,
              createdAt: t(10),
              jobs: [],
            },
          ],
          number: 0,
          totalElements: 1,
          totalPages: 1,
        },
      });
    if (path.endsWith('/reviewers')) return route.fulfill({ json: [] });
    if (path.endsWith('/comments'))
      return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    if (path.endsWith('/metadata'))
      return route.fulfill({
        json: { labels: [], assignees: [], milestone: null, canEdit: false },
      });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/pulls/1');
  const items = page.locator('app-pr-timeline li');
  await expect(items).toHaveCount(5);
  await expect(items.nth(0)).toContainText('demo opened this pull request');
  await expect(items.nth(1)).toContainText('demo added a commit');
  await expect(items.nth(1)).toContainText('aaaaaaa Add feature');
  await expect(items.nth(2)).toContainText('Workflow ci #7: passed');
  await expect(items.nth(3)).toContainText('bob approved these changes');
  await expect(items.nth(4)).toContainText('demo merged this pull request');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
});
