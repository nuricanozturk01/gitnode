import { expect, test } from '@playwright/test';

test('release notes can be generated from the commits since the previous tag', async ({ page }) => {
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
  let query: URLSearchParams | undefined;
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
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
    if (url.pathname === '/api/repos/demo/project/tags') return route.fulfill({ json: [] });
    if (url.pathname === '/api/repos/demo/project/branches')
      return route.fulfill({ json: [{ name: 'main', isDefault: true, lastCommitSha: 'abc' }] });
    if (url.pathname === '/api/repos/demo/project/releases/generate-notes') {
      query = url.searchParams;
      return route.fulfill({
        json: {
          name: 'v2',
          body: "## What's Changed\n\n* Add search (`abc1234`) by Alice\n",
          previousTag: 'v1',
          commitCount: 1,
          truncated: false,
        },
      });
    }
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/releases/new');
  const generate = page.getByRole('button', { name: 'Generate release notes' });
  await expect(generate).toBeDisabled();
  await page.getByLabel('Tag name').fill('v2');
  await generate.click();
  await expect(page.getByLabel('Release notes', { exact: true })).toHaveValue(
    /What's Changed[\s\S]*Add search/,
  );
  await expect(page.locator('#releaseName')).toHaveValue('v2');
  expect(query?.get('tagName')).toBe('v2');
  expect(query?.get('targetCommitish')).toBe('main');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
});
