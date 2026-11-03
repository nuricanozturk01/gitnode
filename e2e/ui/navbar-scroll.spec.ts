import { expect, test } from '@playwright/test';

for (const width of [375, 1280]) {
  test(`navbar keeps its size while scrolling at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 700 });
    await page.goto('/docs');
    const bar = page.locator('app-navbar');
    await expect(bar).toBeVisible();
    const measure = () =>
      bar.evaluate((el) => {
        const r = el.getBoundingClientRect();
        const logo = el.querySelector('.navbar-logo-mark__img')?.getBoundingClientRect();
        return {
          h: Math.round(r.height * 10) / 10,
          w: Math.round(r.width),
          logo: logo ? Math.round(logo.width * 10) / 10 : 0,
        };
      });
    const top = await measure();
    await page.evaluate(() => window.scrollTo(0, 800));
    await page.waitForTimeout(400);
    expect(await measure()).toEqual(top);
  });
}
