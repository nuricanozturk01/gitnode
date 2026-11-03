import { expect, test } from '@playwright/test';

test('directory and file views retry errors and display UTF-8 text', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  const user = {
    id: 'owner-id',
    username: 'demo',
    name: 'Demo',
    email: 'demo@example.org',
    avatarUrl: null,
  };
  let treeFails = true;
  let fileFails = true;
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/repo/demo/project')
      return route.fulfill({
        json: {
          id: 'repo-id',
          name: 'project',
          owner: user,
          defaultBranch: 'main',
          isPrivate: false,
        },
      });
    if (path === '/api/repos/demo/project/tree/main/src') {
      if (treeFails) return route.fulfill({ status: 500 });
      return route.fulfill({
        json: { entries: [{ name: 'hello.txt', path: 'src/hello.txt', type: 'BLOB', size: 20 }] },
      });
    }
    if (path === '/api/repos/demo/project/blob/main/src/hello.txt') {
      if (fileFails) return route.fulfill({ status: 500 });
      return route.fulfill({
        json: {
          path: 'src/hello.txt',
          name: 'hello.txt',
          sha: '123',
          size: 20,
          content: Buffer.from('Merhaba dünya\nİstanbul').toString('base64'),
          isBinary: false,
          language: 'plaintext',
          lineCount: 2,
        },
      });
    }
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/tree/main/src');
  await expect(page.getByText('Directory could not be loaded.')).toBeVisible();
  treeFails = false;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByRole('navigation', { name: 'Repository path' })).toContainText('src');
  await expect(page.locator('[aria-current="page"]', { hasText: 'src' })).toBeVisible();
  await page.getByRole('link', { name: 'hello.txt' }).click();
  await expect(page.getByText('File could not be loaded.')).toBeVisible();
  fileFails = false;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByText('Merhaba dünya')).toBeVisible();
  await expect(page.getByText('İstanbul')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
