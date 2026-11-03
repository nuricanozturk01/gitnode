import { expect, test } from '@playwright/test';

const user = {
  id: 'user-id',
  username: 'demo',
  name: 'Demo',
  email: 'demo@example.org',
  avatarUrl: null,
};
const head = 'a'.repeat(40);

async function setup(
  page: import('@playwright/test').Page,
  opts: { push: boolean; commitStatus?: number },
) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const commits: unknown[] = [];
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/users/me') return route.fulfill({ json: user });
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
    if (path === '/api/repos/demo/project/branches')
      return route.fulfill({ json: [{ name: 'main', isDefault: true, lastCommitSha: head }] });
    if (path === '/api/repos/demo/project/tree/main')
      return route.fulfill({
        json: {
          branch: 'main',
          path: '',
          sha: 't',
          entries: [
            { name: 'src', path: 'src', type: 'TREE', sha: 's', size: 0 },
            { name: 'README', path: 'README', type: 'BLOB', sha: 'r', size: 6 },
          ],
        },
      });
    if (path === '/api/repos/demo/project/tree/main/src')
      return route.fulfill({
        json: {
          branch: 'main',
          path: 'src',
          sha: 's',
          entries: [
            { name: 'app', path: 'src/app', type: 'BLOB', sha: 'a', size: 4 },
            { name: 'util.js', path: 'src/util.js', type: 'BLOB', sha: 'u', size: 30 },
          ],
        },
      });
    if (path === '/api/repos/demo/project/blob/main/src/util.js')
      return route.fulfill({
        json: {
          path: 'src/util.js',
          name: 'util.js',
          sha: 'u',
          size: 30,
          content: Buffer.from('const answer = 42; // note\n').toString('base64'),
          isBinary: false,
          language: 'javascript',
          lineCount: 1,
        },
      });
    if (path === '/api/repos/demo/project/blob/main/README')
      return route.fulfill({
        json: {
          path: 'README',
          name: 'README',
          sha: 'r',
          size: 6,
          content: Buffer.from('hello\n').toString('base64'),
          isBinary: false,
          language: 'plaintext',
          lineCount: 1,
        },
      });
    if (path === '/api/repos/demo/project/blob/main/src/app')
      return route.fulfill({
        json: {
          path: 'src/app',
          name: 'app',
          sha: 'a',
          size: 4,
          content: Buffer.from('app\n').toString('base64'),
          isBinary: false,
          language: 'plaintext',
          lineCount: 1,
        },
      });
    if (path === '/api/repos/demo/project/changes') {
      commits.push(request.postDataJSON());
      if (opts.commitStatus && opts.commitStatus !== 200)
        return route.fulfill({
          status: opts.commitStatus,
          json: {
            message:
              'The branch changed since you opened the editor. Reload to see the latest version.',
          },
        });
      return route.fulfill({ json: { commitSha: 'b'.repeat(40) } });
    }
    if (path.includes('/collaborators') || path.includes('/permissions'))
      return route.fulfill({ json: opts.push ? [{ permissions: ['READ', 'PUSH'] }] : [] });
    return route.fulfill({ status: 404 });
  });
  return commits;
}

test('the . shortcut opens the editor for users who can push and edits are committed together', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1100, height: 800 });
  const commits = await setup(page, { push: true });
  await page.goto('/demo/project/tree/main');
  await page.keyboard.press('.');
  await expect(page).toHaveURL(/\/demo\/project\/editor\/main$/);
  await expect(page.getByRole('heading', { name: /./ }).first())
    .toBeAttached()
    .catch(() => undefined);

  await page.getByRole('treeitem', { name: 'README' }).click();
  await page.getByLabel('Contents of README').fill('hello world\n');
  await page.getByRole('treeitem', { name: 'src' }).click();
  await page.getByRole('treeitem', { name: 'app' }).click();
  await page.getByLabel('Contents of src/app').fill('app edited\n');

  const commit = page.getByRole('button', { name: /Commit changes \(2\)/ });
  await expect(commit).toBeEnabled();
  await commit.click();
  await expect(page.getByLabel('Commit message')).toHaveValue('Update 2 files');
  await page.getByLabel('Commit message').fill('Edit two files');
  await page.getByRole('dialog').getByRole('button', { name: 'Commit changes' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(commits).toEqual([
    {
      branch: 'main',
      expectedHeadSha: head,
      message: 'Edit two files',
      changes: [
        { path: 'README', content: 'hello world\n', delete: false },
        { path: 'src/app', content: 'app edited\n', delete: false },
      ],
    },
  ]);
  await expect(page.getByRole('button', { name: /Commit changes/ }).first()).toBeDisabled();
});

test('typing a period in a text field does not open the editor and users without push cannot open it', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1100, height: 800 });
  await setup(page, { push: true });
  await page.goto('/demo/project/editor/main/README');
  await page.getByLabel('Contents of README').fill('x');
  await page.getByLabel('Contents of README').press('.');
  await expect(page.getByLabel('Contents of README')).toHaveValue('x.');
  await expect(page).toHaveURL(/\/editor\/main\/README$/);
});

test('a stale branch is reported with a way to reload', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await setup(page, { push: true, commitStatus: 400 });
  await page.goto('/demo/project/editor/main/README');
  await page.getByLabel('Contents of README').fill('changed\n');
  await page.getByRole('button', { name: /Commit changes \(1\)/ }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Commit changes' }).click();
  await expect(page.getByRole('alert')).toContainText('The branch changed');
  await expect(page.getByRole('button', { name: 'Reload latest version' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
});

test('a signed-in visitor without push access cannot open the editor with the shortcut', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1100, height: 800 });
  await setup(page, { push: false });
  const other = { id: 'other-id', username: 'other', name: 'Other', email: '', avatarUrl: null };
  await page.route('**/api/repo/other/project', (route) =>
    route.fulfill({
      json: {
        id: 'repo-2',
        name: 'project',
        owner: other,
        defaultBranch: 'main',
        isPrivate: false,
      },
    }),
  );
  await page.route('**/api/repos/other/project/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/tree/main'))
      return route.fulfill({ json: { branch: 'main', path: '', sha: 't', entries: [] } });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/other/project/tree/main');
  await expect(page.getByRole('navigation', { name: 'Repository navigation' })).toBeVisible();
  await page.keyboard.press('.');
  await page.waitForTimeout(500);
  await expect(page).toHaveURL(/\/other\/project\/tree\/main$/);
});

test('code is syntax highlighted and files can be renamed', async ({ page }) => {
  await page.setViewportSize({ width: 1100, height: 800 });
  const commits = await setup(page, { push: true });
  await page.goto('/demo/project/editor/main');
  await page.getByRole('treeitem', { name: 'src' }).click();
  await page.getByRole('treeitem', { name: 'util.js' }).click();
  const overlay = page.getByTestId('editor-highlight');
  await expect(overlay.locator('.hljs-keyword')).toHaveText('const');
  await expect(overlay.locator('.hljs-comment')).toContainText('note');
  await page.getByLabel('Contents of src/util.js').fill('let x = "s";\n');
  await expect(overlay.locator('.hljs-string')).toHaveText('"s"');

  page.once('dialog', (dialog) => dialog.accept('src/helpers.js'));
  await page.getByRole('button', { name: 'Rename' }).click();
  await expect(page.getByText('Renamed from src/util.js')).toBeVisible();
  await page.getByRole('button', { name: /Commit changes \(2\)/ }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Commit changes' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(commits).toHaveLength(1);
  const changes = (commits[0] as { changes: { path: string; delete: boolean; content?: string }[] })
    .changes;
  expect(changes).toEqual([
    { path: 'src/helpers.js', content: 'let x = "s";\n', delete: false },
    { path: 'src/util.js', delete: true },
  ]);
});
