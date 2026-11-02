import { expect, test } from '@playwright/test';

test('clone dialog keeps keyboard focus and restores it when closed', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/repo/demo/project') return route.fulfill({ json: {
      id: 'repo-id', name: 'project', owner: { username: 'demo' }, defaultBranch: 'main', isPrivate: false,
    } });
    if (path.endsWith('/branches')) return route.fulfill({ json: [
      { name: 'main', isDefault: true, lastCommitSha: 'abc' },
    ] });
    if (path.includes('/tree/')) return route.fulfill({ json: { entries: [] } });
    if (path.endsWith('/languages') || path.endsWith('/tags')) return route.fulfill({ json: [] });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project');
  const trigger = page.getByRole('button', { name: 'Clone', exact: true });
  await trigger.click();
  const dialog = page.getByRole('dialog', { name: 'Clone repository' });
  const close = dialog.getByRole('button', { name: 'Close' });
  await expect(close).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  await expect(dialog.getByRole('button', { name: 'Copy' }).last()).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(close).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
