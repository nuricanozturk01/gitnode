import { expect, test } from '@playwright/test';

test('repository settings manage branch protection on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  let rules: {
    id: string;
    branchName: string;
    requiredApprovals: number;
    requiredChecks: string[];
  }[] = [];
  await page.route('**/api/**', (route) => {
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
          description: '',
          topics: [],
        },
      });
    if (path === '/api/repos/demo/project/branch-protections') {
      if (request.method() === 'PUT') rules = [{ id: 'rule-id', ...request.postDataJSON() }];
      return route.fulfill({ json: request.method() === 'PUT' ? rules[0] : rules });
    }
    if (path.endsWith('/branch-protections/rule-id')) {
      rules = [];
      return route.fulfill({ status: 204 });
    }
    if (path === '/api/repos/demo/project/workflows')
      return route.fulfill({
        json: {
          content: [
            {
              id: 'w1',
              name: 'ci',
              filePath: '.gitnode/workflows/ci.yml',
              enabled: true,
              dispatchable: false,
              lastRunStatus: null,
              createdAt: null,
              updatedAt: null,
            },
          ],
          totalElements: 1,
          totalPages: 1,
          page: 0,
          size: 100,
        },
      });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/settings#pullRequests');
  await page.getByLabel('Branch name').fill('main');
  await page.getByLabel('Required approvals').fill('2');
  await page.getByLabel('Required workflows').fill('ci, ci, lint');
  await page.getByRole('button', { name: 'Save protection' }).click();
  await expect(page.getByText('2 required approvals')).toBeVisible();
  await expect(page.getByText('Required checks: ci, lint')).toBeVisible();
  expect(rules[0].requiredChecks).toEqual(['ci', 'lint']);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.getByRole('button', { name: 'Remove main' }).click();
  await page.getByRole('button', { name: 'Remove protection', exact: true }).click();
  await expect(page.getByText('No protected branches.')).toBeVisible();
});
