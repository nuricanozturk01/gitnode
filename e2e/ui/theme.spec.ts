import { expect, test } from '@playwright/test';

test('theme choice persists and system mode follows the browser', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto('/');
  const selector = page.getByRole('combobox', { name: 'Theme' }).last();
  await selector.selectOption('gitnode');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'gitnode');
  await page.reload();
  await expect(selector).toHaveValue('gitnode');
  await selector.selectOption('system');
  await page.emulateMedia({ colorScheme: 'dark' });
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'gitnode');
  await page.emulateMedia({ colorScheme: 'light' });
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'gitnode-light');
});

test('repository navigation scrolls without overflowing the mobile page', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route('**/api/**', async (route) => {
    if (route.request().url().includes('/api/repo/demo/project')) {
      await route.fulfill({
        json: {
          id: 'test',
          name: 'project',
          ownerUsername: 'demo',
          defaultBranch: 'main',
          isPrivate: false,
        },
      });
      return;
    }
    await route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/issues');
  await expect(page.getByRole('navigation', { name: 'Repository navigation' })).toBeVisible();
  const dimensions = await page.locator('.repo-tab-scroll').evaluate((element) => ({
    content: element.scrollWidth,
    width: element.clientWidth,
    page: document.documentElement.scrollWidth,
    viewport: window.innerWidth,
  }));
  expect(dimensions.content).toBeGreaterThan(dimensions.width);
  expect(dimensions.page).toBeLessThanOrEqual(dimensions.viewport);
});
