import { expect, test } from '@playwright/test';

test('accepted collaborators can open routes allowed by their permissions', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'collaborator');
  });
  const owner = { id: 'owner-id', username: 'demo', name: 'Demo', email: 'demo@example.org', avatarUrl: null };
  let permissions = ['PUSH'];
  let status = 'ACCEPTED';
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/users/me') return route.fulfill({ json: { ...owner, username: 'collaborator' } });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: {
      id: 'repo-id', name: 'project', owner, defaultBranch: 'main', isPrivate: false,
    } });
    if (path.endsWith('/collaborators/invitation')) return route.fulfill({ json: { status, permissions } });
    if (path.endsWith('/branches')) return route.fulfill({ json: [
      { name: 'main', isDefault: true, lastCommitSha: 'abc' },
    ] });
    if (path.endsWith('/tags')) return route.fulfill({ json: [] });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/releases/new');
  await expect(page.getByRole('heading', { name: 'Create a new release' })).toBeVisible();
  permissions = ['READ'];
  await page.goto('/demo/project/releases/new');
  await expect(page).toHaveURL(/\/demo\/project$/);
  permissions = ['PULL_REQUEST_CREATE'];
  await page.goto('/demo/project/pulls/new');
  await expect(page.getByRole('heading', { name: 'New pull request' })).toBeVisible();
  status = 'PENDING';
  await page.goto('/demo/project/pulls/new');
  await expect(page).toHaveURL(/\/demo\/project$/);
});
