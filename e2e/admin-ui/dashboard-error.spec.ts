import { expect, test } from '@playwright/test';

test('dashboard distinguishes unavailable statistics from zero', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('admin_token', 'test-token');
    localStorage.setItem('admin_refresh_token', 'test-refresh');
    localStorage.setItem('admin_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('admin_refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('admin_username', 'admin');
  });
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.startsWith('/api/admin/stats') || path.endsWith('/dlq/summary')) return route.fulfill({ status: 500 });
    if (path === '/api/admin/organizations' || path === '/api/admin/audit-logs') {
      return route.fulfill({ json: { content: [], totalPages: 0, totalElements: 0 } });
    }
    return route.fulfill({ json: {} });
  });
  await page.goto('/dashboard');
  await expect(page.getByRole('alert')).toContainText('Some dashboard data could not be loaded.');
  await expect(page.getByText('Activity could not be loaded.')).toBeVisible();
  await expect(page.getByText('Data unavailable').first()).toBeVisible();
  await page.getByRole('button', { name: 'Refresh' }).click();
  await expect(page.getByText('Failed to refresh statistics')).toBeVisible();
});
