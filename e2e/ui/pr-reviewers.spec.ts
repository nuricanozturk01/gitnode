import { expect, test } from '@playwright/test';

test('reviewers can be assigned and merge requires an eligible reviewer', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  let assigned: string[] = [];
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/users/me') return route.fulfill({ json: user });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: {
      id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false,
    } });
    if (path === '/api/repos/demo/project/pulls/1') return route.fulfill({ json: {
      id: 'pr-id', number: 1, title: 'Update', status: 'OPEN', author: user,
      sourceBranch: 'feature', targetBranch: 'main', isDraft: false, description: null,
      mergedBy: null, createdAt: null,
    } });
    if (path.endsWith('/reviews')) {
      if (request.method() === 'POST') return route.fulfill({ json: {
        id: 'review-id', username: 'demo', state: 'COMMENTED', body: request.postDataJSON().body,
        commitSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), createdAt: null,
      } });
      return route.fulfill({ json: { headSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), reviews: [] } });
    }
    if (path.endsWith('/reviewers')) {
      if (request.method() === 'PUT') assigned = request.postDataJSON().usernames;
      return route.fulfill({ json: assigned.map((username) => ({ username, displayName: username, avatarUrl: null, canMerge: true })) });
    }
    if (path.endsWith('/comments')) return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/pulls/1');
  await expect(page.getByText('Assign at least one reviewer with merge permission before merging.')).toBeVisible();
  const merge = page.getByRole('button', { name: 'Merge', exact: true }).first();
  await expect(merge).toBeDisabled();
  await page.getByLabel('Reviewer usernames, separated by commas').fill('demo');
  await page.getByRole('button', { name: 'Save reviewers' }).click();
  await expect(page.getByText('demo (@demo)')).toBeVisible();
  await expect(merge).toBeEnabled();
  await page.getByRole('button', { name: 'Review changes' }).first().click();
  await page.getByLabel('Review comment').fill('Please check this');
  await page.getByRole('button', { name: 'Submit review' }).click();
  await expect(page.getByText('Please check this')).toBeVisible();
});
