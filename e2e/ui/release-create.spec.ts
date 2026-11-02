import { expect, test } from '@playwright/test';

test('first release creates a tag on the default branch', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: 'demo@example.org', avatarUrl: null };
  let submitted: Record<string, unknown> | undefined;
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/users/me') return route.fulfill({ json: user });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: {
      id: 'repo-id', name: 'project', owner: user, defaultBranch: 'develop', isPrivate: false,
    } });
    if (path === '/api/repos/demo/project/tags') return route.fulfill({ json: [] });
    if (path === '/api/repos/demo/project/branches') return route.fulfill({ json: [
      { name: 'develop', isDefault: true, lastCommitSha: 'abc' },
    ] });
    if (path === '/api/repos/demo/project/releases' && request.method() === 'POST') {
      submitted = request.postDataJSON();
      return route.fulfill({ status: 201, json: { id: 'release-id', tagName: 'v1' } });
    }
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/releases/new');
  await expect(page.getByRole('checkbox', { name: /Create a new tag/ })).toBeChecked();
  await page.getByLabel('Tag name').fill('v1');
  await page.getByRole('button', { name: 'Save draft' }).click();
  await expect.poll(() => submitted).toMatchObject({ tagName: 'v1', createNewTag: true, targetCommitish: 'develop', isDraft: true });
});

test('release creation retains the branch when switching tag modes', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: 'demo@example.org', avatarUrl: null };
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/users/me') return route.fulfill({ json: user });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: {
      id: 'repo-id', name: 'project', owner: user, defaultBranch: 'develop', isPrivate: false,
    } });
    if (path === '/api/repos/demo/project/tags') return route.fulfill({ json: [{ name: 'v1', release: null }] });
    if (path === '/api/repos/demo/project/branches') return route.fulfill({ json: [
      { name: 'develop', isDefault: true, lastCommitSha: 'abc' },
    ] });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/releases/new');
  const checkbox = page.getByRole('checkbox', { name: /Create a new tag/ });
  await expect(checkbox).toBeEnabled();
  await checkbox.check();
  await expect(page.getByLabel('Target branch')).toHaveValue('develop');
  await checkbox.uncheck();
  await checkbox.check();
  await expect(page.getByLabel('Target branch')).toHaveValue('develop');
});
