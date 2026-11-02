import { expect, test } from '@playwright/test';

for (const width of [375, 1280]) {
  test(`commit list fits and retries at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.addInitScript(() => localStorage.setItem('gitnode-theme', 'gitnode-light'));
    let fail = true;
    const user = { id: 'owner-id', username: 'demo', name: 'Demo User', email: 'demo@example.org', avatarUrl: null };
    await page.route('**/api/**', (route) => {
      const path = new URL(route.request().url()).pathname;
      if (path === '/api/repo/demo/project') return route.fulfill({ json: {
        id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false,
      } });
      if (path.endsWith('/branches')) return route.fulfill({ json: [
        { name: 'main', isDefault: true, lastCommitSha: 'abcdef' },
      ] });
      if (path.endsWith('/commits')) {
        if (fail) return route.fulfill({ status: 500 });
        return route.fulfill({ json: { items: [
          { sha: 'abcdef012345', shortSha: 'abcdef0', message: 'Improve repository navigation and mobile layout',
            description: 'Update navigation for small screens', author: user, committedAt: '2026-10-30T10:00:00Z',
            stats: { additions: 24, deletions: 8 } },
          { sha: '1234567890ab', shortSha: '1234567', message: 'Add accessible branch controls', description: null,
            author: user, committedAt: '2026-10-29T10:00:00Z', stats: { additions: 18, deletions: 4 } },
        ], totalPages: 1, hasNext: false, hasPrevious: false } });
      }
      return route.fulfill({ status: 404 });
    });
    await page.goto('/demo/project/commits/main');
    await expect(page.getByRole('alert')).toContainText('Failed to load commits');
    fail = false;
    await page.getByRole('button', { name: 'Try again' }).click();
    await expect(page.getByText('Improve repository navigation and mobile layout')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    if (process.env.GITNODE_UI_REVIEW_DIR) {
      await page.screenshot({ path: `${process.env.GITNODE_UI_REVIEW_DIR}/commits-${width}.png`, fullPage: true });
    }
  });
}
