import { expect, test } from '@playwright/test';

test('mobile users list retries failures and wraps long account details', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('admin_token', 'test-token');
    localStorage.setItem('admin_refresh_token', 'test-refresh');
    localStorage.setItem('admin_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('admin_refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('admin_username', 'admin');
  });
  let fail = true;
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/admin/users') {
      if (fail) return route.fulfill({ status: 500 });
      return route.fulfill({ json: { content: [{ id: '1', username: 'very-long-username-without-spaces',
        email: 'a-very-long-email-address-without-spaces@example.org', enabled: true, createdAt: '2026-01-01' }],
        totalPages: 1, totalElements: 1 } });
    }
    return route.fulfill({ json: {} });
  });
  await page.goto('/users');
  await expect(page.getByText('Users could not be loaded.')).toBeVisible();
  fail = false;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByRole('button', { name: 'Disable very-long-username-without-spaces' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  if (process.env.GITNODE_UI_REVIEW_DIR) {
    await page.screenshot({ path: `${process.env.GITNODE_UI_REVIEW_DIR}/admin-users-375.png`, fullPage: true });
  }
});
