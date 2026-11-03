import { expect, test } from '@playwright/test';

test('unknown deep path shows the 404 page and profiles are unaffected', async ({ page }) => {
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/users/demo' || path === '/api/user/demo') {
      return route.fulfill({
        json: { username: 'demo', name: 'Demo', email: '', avatarUrl: null },
      });
    }
    return route.fulfill({ status: 404 });
  });

  await page.goto('/no/such/page/here');
  await expect(page.getByRole('heading', { name: 'This page took a wrong branch' })).toBeVisible();
  await expect(page.getByText('git checkout /no/such/page/here')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Go to home' })).toBeVisible();

  await page.goto('/demo');
  await expect(page.getByRole('heading', { name: 'This page took a wrong branch' })).toHaveCount(0);

  await page.goto('/ghost-user');
  await expect(page.getByRole('heading', { name: 'This page took a wrong branch' })).toHaveCount(0);
});

test('404 page fits a 375px viewport in both themes', async ({ browser }) => {
  for (const colorScheme of ['light', 'dark'] as const) {
    const page = await browser.newPage({ viewport: { width: 375, height: 800 }, colorScheme });
    await page.goto('/a/b/c/d');
    await expect(
      page.getByRole('heading', { name: 'This page took a wrong branch' }),
    ).toBeVisible();
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
    await page.screenshot({ path: `test-results/not-found-${colorScheme}.png`, fullPage: true });
    await page.close();
  }
});
