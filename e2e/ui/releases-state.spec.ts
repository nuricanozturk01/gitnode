import { expect, test } from '@playwright/test';

test('release list retries failures and keeps a tag after its release is deleted', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: 'demo@example.org', avatarUrl: null };
  const release = { id: 'release-id', tagName: 'v1', name: 'First release', author: user, isDraft: false, isPrerelease: false };
  let fail = true;
  let deleted = false;
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/users/me') return route.fulfill({ json: user });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: {
      id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false,
    } });
    if (path === '/api/repos/demo/project/releases/release-id' && request.method() === 'DELETE') {
      deleted = true;
      return route.fulfill({ status: 204 });
    }
    if (path === '/api/repos/demo/project/releases') {
      return fail ? route.fulfill({ status: 500 }) : route.fulfill({ json: deleted ? [] : [release] });
    }
    if (path === '/api/repos/demo/project/tags') return route.fulfill({ json: [{
      name: 'v1', commitShortSha: 'abcdef0', commitMessage: 'Initial commit', release: deleted ? null : release,
    }] });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/releases');
  await expect(page.getByText('Releases and tags could not be loaded.')).toBeVisible();
  fail = false;
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByText('First release')).toBeVisible();
  await page.getByRole('button', { name: 'Delete', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm' }).click();
  await expect(page.getByText('Tags without release')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Delete tag v1' })).toBeVisible();
});
