import { expect, test } from '@playwright/test';

test('mobile navigation opens and closes with the keyboard', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('admin_token', 'test-token');
    localStorage.setItem('admin_refresh_token', 'test-refresh');
    localStorage.setItem('admin_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('admin_refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('admin_username', 'admin');
  });
  await page.route('**/api/**', (route) => route.fulfill({ json: {} }));
  await page.goto('/dashboard');
  const button = page.getByRole('button', { name: 'Open sidebar' });
  await button.focus();
  await page.keyboard.press('Enter');
  await expect(button).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByRole('link', { name: 'Users', exact: true })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(button).toHaveAttribute('aria-expanded', 'false');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
