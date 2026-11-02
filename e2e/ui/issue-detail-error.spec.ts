import { expect, test } from '@playwright/test';

test('issue and comment failures have separate retry states', async ({ page }) => {
  let issueFails = true;
  let commentsFail = true;
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: 'demo@example.org', avatarUrl: null };
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/repo/demo/project') return route.fulfill({ json: {
      id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false,
    } });
    if (path === '/api/repos/demo/project/issues/1') {
      if (issueFails) return route.fulfill({ status: 500 });
      return route.fulfill({ json: {
        id: 'issue-id', number: 1, title: 'Example issue', status: 'OPEN', author: user,
        assignee: null, assignees: [], labels: [], commentCount: 0,
      } });
    }
    if (path.endsWith('/comments')) {
      if (commentsFail) return route.fulfill({ status: 500 });
      return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    }
    if (path.endsWith('/labels') || path.endsWith('/linked-tasks')) return route.fulfill({ json: [] });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/issues/1');
  await expect(page.getByText('Issue could not be loaded.')).toBeVisible();
  issueFails = false;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByRole('heading', { name: 'Example issue' })).toBeVisible();
  await expect(page.getByText('Comments could not be loaded.')).toBeVisible();
  commentsFail = false;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByText('No comments yet.')).toBeVisible();
});
