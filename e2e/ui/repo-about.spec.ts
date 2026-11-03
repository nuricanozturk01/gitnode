import { expect, test } from '@playwright/test';

for (const empty of [true, false]) {
  test(`repository details can be edited${empty ? ' in an empty repository' : ''}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.addInitScript(() => {
      localStorage.setItem('token', 'test-token');
      localStorage.setItem('refresh_token', 'test-refresh');
      localStorage.setItem('expires_at', String(Date.now() + 60_000));
      localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
      localStorage.setItem('username', 'demo');
    });
    const user = {
      id: 'user-id',
      username: 'demo',
      name: 'Demo',
      email: 'demo@example.org',
      avatarUrl: null,
    };
    let repo = {
      id: 'repo-id',
      name: 'project',
      owner: user,
      defaultBranch: 'main',
      isPrivate: false,
      description: null as string | null,
      topics: [] as string[],
    };
    await page.route('**/api/**', (route) => {
      const request = route.request();
      const path = new URL(request.url()).pathname;
      if (path === '/api/users/me') return route.fulfill({ json: user });
      if (path === '/api/repo/demo/project') {
        if (request.method() === 'PATCH') repo = { ...repo, ...request.postDataJSON() };
        return route.fulfill({ json: repo });
      }
      if (path.endsWith('/branches'))
        return route.fulfill({
          json: empty ? [] : [{ name: 'main', isDefault: true, lastCommitSha: 'abc' }],
        });
      if (path.includes('/tree/')) return route.fulfill({ json: { entries: [] } });
      if (path.endsWith('/languages') || path.endsWith('/tags')) return route.fulfill({ json: [] });
      return route.fulfill({ status: 404 });
    });
    await page.goto('/demo/project');
    const edit = page.getByRole('button', { name: 'Edit repository details' });
    await edit.click();
    const dialog = page.getByRole('dialog', { name: 'Edit repository details' });
    await expect(dialog.getByLabel('Description')).toBeFocused();
    await dialog.getByLabel('Description').fill('Project description');
    await dialog.getByLabel('Topics').fill('invalid topic');
    await dialog.getByRole('button', { name: 'Save changes' }).click();
    await expect(dialog.getByRole('alert')).toContainText('Use up to 6 topics');
    await dialog.getByLabel('Topics').fill('Angular, open-source');
    await dialog.getByRole('button', { name: 'Save changes' }).click();
    await expect(
      page.getByRole('region', { name: 'About' }).getByText('Project description'),
    ).toBeVisible();
    await expect(page.getByLabel('Repository topics')).toContainText('angular');
    await expect(edit).toBeFocused();
    await edit.click();
    await dialog.getByLabel('Description').fill('');
    await dialog.getByLabel('Topics').fill('');
    await dialog.getByRole('button', { name: 'Save changes' }).click();
    await expect(page.getByText('No description provided.')).toBeVisible();
    expect(repo.topics).toEqual([]);
    expect(repo.description).toBeNull();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
  });
}

test('about edit requires repository settings permission', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'viewer');
  });
  let permissions = ['READ'];
  const owner = { id: 'owner-id', username: 'demo', email: 'demo@example.org', avatarUrl: null };
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/users/me') return route.fulfill({ json: { ...owner, username: 'viewer' } });
    if (path === '/api/repo/demo/project')
      return route.fulfill({
        json: {
          id: 'repo-id',
          name: 'project',
          owner,
          defaultBranch: 'main',
          isPrivate: false,
          description: null,
          topics: [],
        },
      });
    if (path.endsWith('/collaborators/invitation'))
      return route.fulfill({ json: { status: 'ACCEPTED', permissions } });
    if (path.endsWith('/branches')) return route.fulfill({ json: [] });
    if (path.includes('/tree/')) return route.fulfill({ json: { entries: [] } });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project');
  await expect(page.getByText('No description provided.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Edit repository details' })).toHaveCount(0);
  permissions = ['SETTINGS_WRITE'];
  await page.reload();
  await expect(page.getByRole('button', { name: 'Edit repository details' })).toBeVisible();
});
