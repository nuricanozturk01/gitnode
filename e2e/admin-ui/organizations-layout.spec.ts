import { expect, test } from '@playwright/test';

test('mobile organizations list retries and keeps controls visible', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('admin_token', 'test-token');
    localStorage.setItem('admin_refresh_token', 'test-refresh');
    localStorage.setItem('admin_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('admin_refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('admin_username', 'admin');
  });
  let calls = 0;
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/admin/organizations') {
      calls++;
      if (calls === 2) return route.fulfill({ status: 500 });
      return route.fulfill({
        json: {
          content:
            calls === 1
              ? []
              : [
                  {
                    name: 'Long organization name',
                    slug: 'a-very-long-organization-name-without-spaces',
                    emailDomains: ['example.org'],
                    ssoEnabled: false,
                    ldapEnabled: false,
                    ldapConfigured: false,
                  },
                ],
          totalPages: 1,
          totalElements: 1,
        },
      });
    }
    return route.fulfill({ json: {} });
  });
  await page.goto('/organizations');
  await expect(page.getByText('Organizations could not be loaded.')).toBeVisible();
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(
    page.getByRole('link', { name: 'Edit Long organization name' }).first(),
  ).toBeVisible();
  await expect(
    page.getByRole('checkbox', { name: 'Enable SAML for Long organization name' }).first(),
  ).toBeAttached();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
