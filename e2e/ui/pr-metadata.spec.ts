import { expect, test } from '@playwright/test';

test('labels, assignees and milestone are shown and can be edited on a pull request', async ({
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
  const bug = { id: 'l1', name: 'bug', color: 'd73a4a', description: null };
  const docs = { id: 'l2', name: 'docs', color: '0075ca', description: null };
  let metadata: Record<string, unknown> = {
    labels: [bug],
    assignees: [],
    milestone: null,
    canEdit: true,
  };
  let saved: unknown;
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
          title: 'Update',
          status: 'OPEN',
          author: user,
          sourceBranch: 'feature',
          targetBranch: 'main',
          isDraft: false,
          description: null,
          mergedBy: null,
          createdAt: null,
        },
      });
    if (path === '/api/repos/demo/project/pulls/1/metadata') {
      if (request.method() === 'PUT') {
        saved = request.postDataJSON();
        metadata = {
          labels: [bug, docs],
          assignees: [{ name: 'demo', email: '', username: 'demo', avatarUrl: '' }],
          milestone: { id: 'm1', title: 'v1', status: 'OPEN' },
          canEdit: true,
        };
      }
      return route.fulfill({ json: metadata });
    }
    if (path === '/api/repos/demo/project/labels') return route.fulfill({ json: [bug, docs] });
    if (path === '/api/repos/demo/project/milestones')
      return route.fulfill({
        json: [{ id: 'm1', title: 'v1', description: null, dueDate: null, status: 'OPEN' }],
      });
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
  await expect(page.getByText('bug', { exact: true })).toBeVisible();
  await expect(page.getByText('No one assigned')).toBeVisible();

  await page.locator('app-pr-metadata').getByRole('button', { name: 'Edit' }).click();
  await page.locator('app-pr-metadata').getByText('docs', { exact: true }).click();
  await page.getByLabel(/Assignees/).fill('demo');
  await page.locator('#pm-milestone').selectOption('m1');
  await page.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('docs', { exact: true })).toBeVisible();
  await expect(page.locator('app-pr-metadata').getByText('v1')).toBeVisible();
  expect(saved).toEqual({ labelIds: ['l1', 'l2'], assignees: ['demo'], milestoneId: 'm1' });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
});
