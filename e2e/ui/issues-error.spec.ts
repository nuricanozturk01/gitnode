import { expect, test } from '@playwright/test';

test('issue list distinguishes loading errors from an empty repository', async ({ page }) => {
  let fail = true;
  const owner = { username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/repo/demo/project')
      return route.fulfill({
        json: {
          id: 'repo-id',
          name: 'project',
          owner,
          defaultBranch: 'main',
          isPrivate: false,
        },
      });
    if (path === '/api/repos/demo/project/issues') {
      return fail
        ? route.fulfill({ status: 500 })
        : route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    }
    if (path === '/api/repos/demo/project/labels') return route.fulfill({ json: [] });
    if (path === '/api/repos/demo/project/milestones') return route.fulfill({ json: [] });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/issues');
  await expect(page.getByRole('alert')).toContainText('Issues could not be loaded.');
  fail = false;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByText('No open issues')).toBeVisible();
});

test('pull request list allows retry after an API error', async ({ page }) => {
  let fail = true;
  const owner = { username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/repo/demo/project')
      return route.fulfill({
        json: {
          id: 'repo-id',
          name: 'project',
          owner,
          defaultBranch: 'main',
          isPrivate: false,
        },
      });
    if (path === '/api/repos/demo/project/pulls')
      return fail
        ? route.fulfill({ status: 500 })
        : route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/pulls');
  await expect(page.getByRole('alert')).toContainText('Pull requests could not be loaded.');
  fail = false;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByText('No open pull requests')).toBeVisible();
});
