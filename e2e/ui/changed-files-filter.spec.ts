import { expect, test } from '@playwright/test';

const user = { id: 'user-id', username: 'demo', name: 'Demo', email: '', avatarUrl: null };
const sha = 'c'.repeat(40);
const file = (path: string) => ({ oldPath: path, newPath: path, changeType: 'MODIFY', additions: 1, deletions: 0, hunks: [] });
const files = ['src/app.ts', 'src/util.ts', 'docs/guide.md', 'README.md'].map(file);

test('changed files can be filtered on a commit and on a pull request', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/users/me') return route.fulfill({ json: user });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: { id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false } });
    if (path === `/api/repos/demo/project/commits/${sha}`) return route.fulfill({ json: { sha, shortSha: 'ccccccc', message: 'Big change', description: null, author: { name: 'Demo', username: 'demo', email: '', avatarUrl: '' }, committedAt: new Date().toISOString(), parentShas: [], stats: { filesChanged: 4, additions: 4, deletions: 0 }, files } });
    if (path === '/api/repos/demo/project/pulls/1') return route.fulfill({ json: { id: 'pr-id', number: 1, title: 'Update', status: 'OPEN', author: user, sourceBranch: 'feature', targetBranch: 'main', isDraft: false, description: null, mergedBy: null, createdAt: null } });
    if (path === '/api/repos/demo/project/pulls/1/diff') return route.fulfill({ json: files });
    if (path.endsWith('/reviewers')) return route.fulfill({ json: [] });
    if (path.endsWith('/reviews')) return route.fulfill({ json: { headSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), reviews: [] } });
    if (path.endsWith('/comments')) return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    return route.fulfill({ status: 404 });
  });

  await page.goto(`/demo/project/commit/${sha}`);
  await expect(page.getByText('src/app.ts').first()).toBeVisible();
  await page.getByLabel('Filter changed files').fill('guide');
  await expect(page.getByText('docs/guide.md').first()).toBeVisible();
  await expect(page.getByText('src/app.ts')).toHaveCount(0);
  await expect(page.getByText('1 of 4 files')).toBeVisible();
  await page.getByLabel('Filter changed files').fill('zzz');
  await expect(page.getByText('No changed files match "zzz".')).toBeVisible();

  await page.goto('/demo/project/pulls/1');
  await page.getByRole('tab', { name: 'Files changed' }).click();
  await page.getByLabel('Filter changed files').fill('src/');
  await expect(page.getByText('2 of 4 files')).toBeVisible();
  await expect(page.getByText('docs/guide.md')).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
