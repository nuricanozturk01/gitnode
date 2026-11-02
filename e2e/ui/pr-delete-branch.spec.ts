import { expect, test } from '@playwright/test';

test('a merged pull request offers to delete its source branch', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  let deleted = false;
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/users/me') return route.fulfill({ json: user });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: { id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false } });
    if (path === '/api/repos/demo/project/pulls/1') return route.fulfill({ json: { id: 'pr-id', number: 1, title: 'Done', status: 'MERGED', author: user, mergedBy: user, sourceBranch: 'feature', targetBranch: 'main', isDraft: false, description: null, createdAt: null } });
    if (path === '/api/repos/demo/project/branches/feature' && request.method() === 'DELETE') { deleted = true; return route.fulfill({ status: 204 }); }
    if (path === '/api/repos/demo/project/branches') return route.fulfill({ json: deleted ? [{ name: 'main', isDefault: true }] : [{ name: 'main', isDefault: true }, { name: 'feature', isDefault: false }] });
    if (path.endsWith('/reviews')) return route.fulfill({ json: { headSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), reviews: [] } });
    if (path.endsWith('/reviewers')) return route.fulfill({ json: [] });
    if (path.endsWith('/comments')) return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/pulls/1');
  await expect(page.getByText('branch can be deleted')).toBeVisible();
  await page.getByRole('button', { name: 'Delete branch', exact: true }).click();
  await page.locator('.btn-error', { hasText: 'Delete branch' }).click();
  await expect(page.getByText('branch can be deleted')).toHaveCount(0);
  expect(deleted).toBe(true);
});
