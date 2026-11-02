import { expect, test } from '@playwright/test';

for (const width of [375, 1280]) {
  test(`sign-in remains usable at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 800 });
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
    await expect(page.getByLabel('Username or Email')).toBeVisible();
    await expect(page.getByLabel('Password', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
    await page.getByLabel('Username or Email').focus();
    await page.keyboard.press('Tab');
    await expect(page.getByLabel('Username or Email')).toHaveAttribute('aria-invalid', 'true');
    await expect(page.locator('#login-username-error')).toHaveText('Required');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
