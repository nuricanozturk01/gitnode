import { expect, test } from '@playwright/test';

test('a draft pull request can be marked ready and converted back', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  let isDraft = true;
  const patches: unknown[] = [];
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/users/me') return route.fulfill({ json: user });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: { id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false } });
    if (path === '/api/repos/demo/project/pulls/1') {
      if (request.method() === 'PATCH') {
        patches.push(request.postDataJSON());
        isDraft = request.postDataJSON().isDraft;
      }
      return route.fulfill({ json: { id: 'pr-id', number: 1, title: 'Update', status: 'OPEN', author: user, sourceBranch: 'feature', targetBranch: 'main', isDraft, description: null, mergedBy: null, createdAt: null } });
    }
    if (path.endsWith('/merge-requirements')) return route.fulfill({ json: { protectedBranch: false, requiredApprovals: 0, currentApprovals: 0, changesRequestedBy: [], checks: [], satisfied: true } });
    if (path.endsWith('/conflicts')) return route.fulfill({ json: { mergeable: true, sourceSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), files: [] } });
    if (path.endsWith('/reviews')) return route.fulfill({ json: { headSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), reviews: [] } });
    if (path.endsWith('/reviewers')) return route.fulfill({ json: [] });
    if (path.endsWith('/comments')) return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/pulls/1');
  await expect(page.getByText('Draft', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Ready for review' }).click();
  await expect(page.getByText('Draft', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Convert to draft' }).click();
  await expect(page.getByText('Draft', { exact: true })).toBeVisible();
  expect(patches).toEqual([{ isDraft: false }, { isDraft: true }]);
});
