import { expect, test } from '@playwright/test';

test('protected branch requirements are visible and block merging until satisfied', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  let requirements: object = {
    protectedBranch: true, requiredApprovals: 2, currentApprovals: 1, changesRequestedBy: ['bob'],
    checks: [{ name: 'ci', state: 'FAILURE' }, { name: 'lint', state: 'MISSING' }], satisfied: false,
  };
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/users/me') return route.fulfill({ json: user });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: { id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false } });
    if (path === '/api/repos/demo/project/pulls/1') return route.fulfill({ json: { id: 'pr-id', number: 1, title: 'Update', status: 'OPEN', author: user, sourceBranch: 'feature', targetBranch: 'main', isDraft: false, description: null, mergedBy: null, createdAt: null } });
    if (path.endsWith('/merge-requirements')) return route.fulfill({ json: requirements });
    if (path.endsWith('/conflicts')) return route.fulfill({ json: { mergeable: true, sourceSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), files: [] } });
    if (path.endsWith('/reviews')) return route.fulfill({ json: { headSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), reviews: [] } });
    if (path.endsWith('/reviewers')) return route.fulfill({ json: [{ username: 'demo', displayName: 'demo', avatarUrl: null, canMerge: true }] });
    if (path.endsWith('/comments')) return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/pulls/1');

  await expect(page.getByRole('heading', { name: 'Merging into main is blocked' })).toBeVisible();
  await expect(page.getByText('Approvals: 1 of 2 required')).toBeVisible();
  await expect(page.getByText('Changes requested by bob')).toBeVisible();
  await expect(page.getByText('Required workflow ci: Failed')).toBeVisible();
  await expect(page.getByText('Required workflow lint: Not run for this commit')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Merge', exact: true }).first()).toBeDisabled();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

  requirements = { protectedBranch: true, requiredApprovals: 0, currentApprovals: 0, changesRequestedBy: [], checks: [], satisfied: true };
  await page.reload();
  await expect(page.getByRole('heading', { name: 'All requirements for main are met' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Merge', exact: true }).first()).toBeEnabled();
});
