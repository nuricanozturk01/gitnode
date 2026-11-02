import { expect, test } from '@playwright/test';

for (const width of [375, 1280]) {
  test(`landing and docs fit at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 800 });
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.locator('link[rel="icon"][type="image/svg+xml"]')).toHaveAttribute('href', '/gitnode-mark.svg');
    await expect(page.locator('link[rel="apple-touch-icon"]')).toHaveAttribute('href', '/gitnode-mark-180.png');
    const logo = await page.request.get('/gitnode-mark.svg');
    expect(logo.ok()).toBe(true);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.getByRole('button', { name: 'SSH', exact: true }).click();
    await expect(page.getByRole('button', { name: 'SSH', exact: true })).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator('.landing-v2-command code')).toContainText('ssh://');
    if (process.env.GITNODE_UI_REVIEW_DIR) {
      await page.screenshot({ path: `${process.env.GITNODE_UI_REVIEW_DIR}/landing-${width}.png`, fullPage: true });
    }
    await page.goto('/docs');
    await expect(page.getByRole('heading', { name: 'GitNode Documentation' })).toBeVisible();
    const actions = page.getByRole('button', { name: 'Actions (CI/CD)' });
    await actions.click();
    await expect(actions).toHaveAttribute('aria-pressed', 'true');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    if (process.env.GITNODE_UI_REVIEW_DIR) {
      await page.screenshot({ path: `${process.env.GITNODE_UI_REVIEW_DIR}/docs-${width}.png`, fullPage: true });
    }
  });
}

for (const [theme, light] of [['gitnode', false], ['gitnode-light', true]] as const) {
  test(`landing and docs surfaces match ${theme}`, async ({ page }) => {
    await page.addInitScript((value) => localStorage.setItem('gitnode-theme', value), theme);
    await page.goto('/');
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
    const selectors = ['.landing-v2-hero', '.landing-v2-preview-top', '.landing-v2-filebox',
      '.landing-v2-ai', '.landing-v2-ai-card', '.landing-v2-terminal', '.landing-v2-cta'];
    for (const selector of selectors) {
      const channels = await page.locator(selector).evaluate((element) => {
        const canvas = document.createElement('canvas');
        canvas.width = canvas.height = 1;
        const context = canvas.getContext('2d')!;
        context.fillStyle = getComputedStyle(element).backgroundColor;
        context.fillRect(0, 0, 1, 1);
        return Array.from(context.getImageData(0, 0, 1, 1).data).slice(0, 3);
      });
      if (light) expect(Math.min(...channels), selector).toBeGreaterThan(175);
      else expect(Math.max(...channels), selector).toBeLessThan(100);
    }
    await page.goto('/docs');
    const docsChannels = await page.locator('.docs-hero').evaluate((element) => {
      const canvas = document.createElement('canvas');
      const context = canvas.getContext('2d')!;
      context.fillStyle = getComputedStyle(element).backgroundColor;
      context.fillRect(0, 0, 1, 1);
      return Array.from(context.getImageData(0, 0, 1, 1).data).slice(0, 3);
    });
    if (light) expect(Math.min(...docsChannels)).toBeGreaterThan(175);
    else expect(Math.max(...docsChannels)).toBeLessThan(100);
  });
}
