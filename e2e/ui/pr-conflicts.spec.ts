import { expect, test } from '@playwright/test';

const marked = 'one\n<<<<<<< feature\nFEATURE\n=======\nMAIN\n>>>>>>> main\nthree\n';

async function setup(
  page: import('@playwright/test').Page,
  files: object[],
  resolveBody: { value: unknown },
) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  let resolved = false;
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
    if (path === '/api/repos/demo/project/pulls/1')
      return route.fulfill({
        json: {
          id: 'pr-id',
          number: 1,
          title: 'Update',
          status: 'OPEN',
          author: user,
          sourceBranch: 'feature',
          targetBranch: 'main',
          isDraft: false,
          description: null,
          mergedBy: null,
          createdAt: null,
        },
      });
    if (path === '/api/repos/demo/project/pulls/1/conflicts/resolve') {
      resolveBody.value = request.postDataJSON();
      resolved = true;
      return route.fulfill({ json: { commitSha: 'c'.repeat(40) } });
    }
    if (path === '/api/repos/demo/project/pulls/1/conflicts') {
      return route.fulfill({
        json: resolved
          ? { mergeable: true, sourceSha: 'c'.repeat(40), targetSha: 'b'.repeat(40), files: [] }
          : { mergeable: false, sourceSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), files },
      });
    }
    if (path.endsWith('/reviews'))
      return route.fulfill({
        json: { headSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), reviews: [] },
      });
    if (path.endsWith('/reviewers'))
      return route.fulfill({
        json: [{ username: 'demo', displayName: 'demo', avatarUrl: null, canMerge: true }],
      });
    if (path.endsWith('/comments'))
      return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    return route.fulfill({ status: 404 });
  });
}

test('conflicts block merging and simple conflicts are resolved in the browser', async ({
  page,
}) => {
  await page.setViewportSize({ width: 375, height: 812 });
  const body: { value: unknown } = { value: null };
  await setup(page, [{ path: 'a.txt', resolvable: true, reason: null, content: marked }], body);
  await page.goto('/demo/project/pulls/1');

  await expect(
    page.getByRole('heading', { name: 'This branch has conflicts that must be resolved' }),
  ).toBeVisible();
  await expect(page.getByLabel('Conflicting files').getByText('a.txt')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Merge', exact: true }).first()).toBeDisabled();
  await expect(
    page.getByRole('button', { name: /Merge pull request|Create merge commit|Merge$/ }).last(),
  ).toBeDisabled();

  await page.getByRole('button', { name: 'Resolve conflicts' }).click();
  const commit = page.getByRole('button', { name: 'Commit merge' });
  await expect(commit).toBeDisabled();
  await expect(page.getByText('1 of 1 files still have conflict markers')).toBeVisible();

  await page.getByRole('button', { name: 'Accept both' }).click();
  await expect(page.getByText('All conflicts resolved')).toBeVisible();
  await expect(page.getByLabel('Contents of a.txt')).toHaveValue('one\nFEATURE\nMAIN\nthree\n');
  await expect(commit).toBeEnabled();
  await commit.click();

  await expect(
    page.getByRole('heading', { name: 'This branch has conflicts that must be resolved' }),
  ).toHaveCount(0);
  expect(body.value).toEqual({
    sourceSha: 'a'.repeat(40),
    targetSha: 'b'.repeat(40),
    files: [{ path: 'a.txt', content: 'one\nFEATURE\nMAIN\nthree\n' }],
  });
});

test('conflicts that cannot be resolved in the browser show local instructions', async ({
  page,
}) => {
  const body: { value: unknown } = { value: null };
  await setup(
    page,
    [
      {
        path: 'logo.png',
        resolvable: false,
        reason: 'Binary file, deletion, or rename conflict; resolve it locally',
        content: null,
      },
    ],
    body,
  );
  await page.goto('/demo/project/pulls/1');

  await expect(page.getByText('resolve it locally')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Resolve conflicts' })).toHaveCount(0);
  await expect(page.getByText('git merge origin/main')).toBeVisible();
});
