import { expect, test } from '@playwright/test';

test('repository labels can be created, filtered and assigned to an issue', async ({ page }) => {
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
    email: 'demo@example.org',
    avatarUrl: null,
    name: 'Demo',
  };
  let labels: { id: string; name: string; color: string; description: string }[] = [];
  let assigned: typeof labels = [];
  let lastFilter = '';
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === '/api/users/me') return route.fulfill({ json: user });
    if (url.pathname === '/api/repo/demo/project')
      return route.fulfill({
        json: {
          id: 'repo-id',
          name: 'project',
          owner: user,
          defaultBranch: 'main',
          isPrivate: false,
        },
      });
    if (url.pathname === '/api/repos/demo/project/labels') {
      if (request.method() === 'POST') labels = [{ id: 'label-id', ...request.postDataJSON() }];
      return route.fulfill({ json: request.method() === 'POST' ? labels[0] : labels });
    }
    if (url.pathname === '/api/repos/demo/project/labels/label-id') {
      if (request.method() === 'PUT') {
        labels = [{ id: 'label-id', ...request.postDataJSON() }];
        return route.fulfill({ json: labels[0] });
      }
      if (request.method() === 'DELETE') {
        labels = [];
        return route.fulfill({ status: 204 });
      }
    }
    if (url.pathname === '/api/repos/demo/project/issues') {
      lastFilter = url.searchParams.get('labelId') ?? '';
      return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    }
    if (url.pathname === '/api/repos/demo/project/issues/1') {
      if (request.method() === 'PATCH') {
        const body = request.postDataJSON();
        if (body.addLabelId) assigned = labels.filter((label) => label.id === body.addLabelId);
        if (body.removeLabelId) assigned = [];
      }
      return route.fulfill({
        json: {
          id: 'issue-id',
          number: 1,
          title: 'Issue',
          status: 'OPEN',
          author: user,
          assignee: null,
          assignees: [],
          labels: assigned,
          commentCount: 0,
        },
      });
    }
    if (url.pathname.endsWith('/comments')) {
      return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    }
    return route.fulfill({ status: 404 });
  });

  await page.goto('/demo/project/issues');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.getByText('Manage labels').click();
  await page.getByLabel('Name', { exact: true }).fill('bug');
  await page.getByRole('button', { name: 'Create label' }).click();
  await expect(page.getByRole('option', { name: 'bug' })).toHaveCount(1);
  await page.getByRole('button', { name: 'Edit bug' }).click();
  await page.getByLabel('Name', { exact: true }).fill('defect');
  await page.getByRole('button', { name: 'Save label' }).click();
  await expect(page.getByRole('option', { name: 'defect' })).toHaveCount(1);
  await page.getByLabel('Filter by label').selectOption('label-id');
  await expect.poll(() => lastFilter).toBe('label-id');
  await page.goto('/demo/project/issues/1');
  await page.getByText('Edit labels').click();
  await page.getByRole('checkbox', { name: 'defect' }).check();
  await expect(page.locator('[aria-label="Issue labels"]')).toContainText('defect');
  await page.getByRole('checkbox', { name: 'defect' }).uncheck();
  await expect(page.locator('[aria-label="Issue labels"]')).toContainText('None');
  await page.goto('/demo/project/issues');
  await page.getByText('Manage labels').click();
  await page.getByRole('button', { name: 'Delete defect' }).click();
  await page.getByRole('button', { name: 'Delete', exact: true }).click();
  await expect(page.getByRole('option', { name: 'defect' })).toHaveCount(0);
});
