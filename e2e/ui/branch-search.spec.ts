import { expect, test } from '@playwright/test';

test('branches can be searched, show divergence and link to a new pull request', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  const branch = (name: string, extra: object) => ({ name, lastCommitSha: 'a'.repeat(40), lastCommitShortSha: 'aaaaaaa', lastCommitMessage: 'msg', lastCommitAuthor: 'demo', lastCommitDate: new Date().toISOString(), isDefault: false, isProtected: false, ahead: 0, behind: 0, ...extra });
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/users/me') return route.fulfill({ json: user });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: { id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false } });
    if (path === '/api/repos/demo/project/branches') return route.fulfill({ json: [branch('main', { isDefault: true }), branch('feature/search', { ahead: 3, behind: 500 }), branch('fix-typo', { ahead: 0, behind: 2 })] });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/branches');
  await expect(page.getByText('3 ahead')).toBeVisible();
  await expect(page.getByText('500+ behind')).toBeVisible();
  await expect(page.getByText('0 ahead')).toBeVisible();
  const link = page.getByRole('link', { name: 'New pull request from feature/search' });
  await expect(link).toHaveAttribute('href', /\/demo\/project\/pulls\/new\?head=feature%2Fsearch&base=main/);
  await expect(page.getByRole('link', { name: /New pull request from fix-typo/ })).toHaveCount(0);

  await page.getByLabel('Search branches').fill('typo');
  await expect(page.getByRole('link', { name: 'fix-typo' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'feature/search', exact: true })).toHaveCount(0);
  await page.getByLabel('Search branches').fill('zzz');
  await expect(page.getByRole('status')).toContainText('No branches match');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
