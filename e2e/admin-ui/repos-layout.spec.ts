import { expect, test } from '@playwright/test';

test('mobile repository list shows errors and wraps long names', async ({ page }) => {
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
    if (path === '/api/admin/repos') {
      if (fail) return route.fulfill({ status: 500 });
      return route.fulfill({
        json: {
          content: [
            {
              id: '1',
              fullName: 'owner/a-very-long-repository-name-without-spaces',
              defaultBranch: 'main',
              isPrivate: true,
              isArchived: false,
              createdAt: '2026-01-01',
            },
          ],
          totalPages: 1,
          totalElements: 1,
        },
      });
    }
    return route.fulfill({ json: {} });
  });
  await page.goto('/repos');
  await expect(page.getByText('Repositories could not be loaded.')).toBeVisible();
  fail = false;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(
    page.getByText('owner/a-very-long-repository-name-without-spaces').first(),
  ).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
