import { expect, test } from '@playwright/test';

for (const theme of ['gitnode-light', 'gitnode']) {
  for (const width of [375, 1280]) {
    test(`branch list fits at ${width}px in ${theme}`, async ({ page }) => {
      await page.setViewportSize({ width, height: 850 });
      await page.addInitScript((selectedTheme) => {
        localStorage.setItem('gitnode-theme', selectedTheme);
        localStorage.setItem('token', 'test-token');
        localStorage.setItem('refresh_token', 'test-refresh');
        localStorage.setItem('expires_at', String(Date.now() + 60_000));
        localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
        localStorage.setItem('username', 'demo');
      }, theme);
      const user = { id: 'user-id', username: 'demo', name: 'Demo', email: 'demo@example.org', avatarUrl: null };
      await page.route('**/api/**', async (route) => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/users/me') return route.fulfill({ json: user });
        if (path === '/api/repo/demo/project') return route.fulfill({ json: {
          id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false,
        } });
        if (path.endsWith('/branches')) return route.fulfill({ json: [
          { name: 'main', isDefault: true, lastCommitSha: '', isProtected: false },
          { name: 'feature/a-very-long-branch-name-without-spaces-to-test-wrapping', isDefault: false,
            isProtected: true, lastCommitSha: 'abcdef012345', lastCommitShortSha: 'abcdef0',
            lastCommitMessage: 'Update branch', lastCommitAuthor: 'demo', lastCommitDate: '2026-01-01T00:00:00Z' },
        ] });
        return route.fulfill({ status: 404 });
      });
      await page.goto('/demo/project/branches');
      await expect(page.getByText('No commits yet')).toBeVisible();
      await expect(page.getByRole('button', { name: /Delete branch feature/ })).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      if (process.env.GITNODE_UI_REVIEW_DIR) {
        await page.screenshot({ path: `${process.env.GITNODE_UI_REVIEW_DIR}/branches-${theme}-${width}.png`, fullPage: true });
      }
      if (width === 375 && theme === 'gitnode-light') {
        const trigger = page.getByRole('button', { name: 'New branch' });
        await trigger.click();
        const dialog = page.getByRole('dialog', { name: 'Create branch' });
        await expect(page.getByLabel('Branch name')).toBeFocused();
        await page.keyboard.press('Shift+Tab');
        await expect(dialog.getByRole('button', { name: 'Cancel' })).toBeFocused();
        await page.keyboard.press('Escape');
        await expect(dialog).toHaveCount(0);
        await expect(trigger).toBeFocused();
      }
    });
  }
}
