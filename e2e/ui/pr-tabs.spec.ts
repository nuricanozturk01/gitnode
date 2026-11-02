import { expect, test } from '@playwright/test';

test('pull request sections support keyboard navigation on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  const user = { username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/reviewers')) return route.fulfill({ json: [] });
    if (path.endsWith('/reviews')) return route.fulfill({ json: { headSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), reviews: [] } });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: {
      id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false,
    } });
    if (path === '/api/repos/demo/project/pulls/1') return route.fulfill({ json: {
      id: 'pr-id', number: 1, title: 'Improve navigation', status: 'OPEN', author: user,
      sourceBranch: 'feature', targetBranch: 'main', isDraft: false, description: null,
      mergedBy: null, createdAt: null,
    } });
    if (path.endsWith('/comments')) return route.fulfill({ json: {
      content: [], number: 0, totalElements: 0, totalPages: 0,
    } });
    if (path.endsWith('/commits')) return route.fulfill({ json: [] });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/pulls/1');
  const conversation = page.getByRole('tab', { name: 'Conversation' });
  const commits = page.getByRole('tab', { name: 'Commits' });
  await conversation.focus();
  await page.keyboard.press('ArrowRight');
  await expect(commits).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(commits).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('tabpanel', { name: 'Commits' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('pull request commits and files can be retried after loading fails', async ({ page }) => {
  const user = { username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  const attempts: Record<string, number> = {};
  const failing = new Set(['/commits', '/diff', '/actions/runs']);
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/reviewers')) return route.fulfill({ json: [] });
    if (path.endsWith('/reviews')) return route.fulfill({ json: { headSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), reviews: [] } });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: {
      id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false,
    } });
    if (path === '/api/repos/demo/project/pulls/1') return route.fulfill({ json: {
      id: 'pr-id', number: 1, title: 'Improve navigation', status: 'OPEN', author: user,
      sourceBranch: 'feature', targetBranch: 'main', isDraft: false, description: null,
      mergedBy: null, createdAt: null,
    } });
    if (path.endsWith('/comments')) return route.fulfill({ json: {
      content: [], number: 0, totalElements: 0, totalPages: 0,
    } });
    if (path.endsWith('/commits') || path.endsWith('/diff') || path.endsWith('/actions/runs')) {
      // Other parts of the page (the activity timeline) read these too, so failures are switched
      // off explicitly instead of being counted per request.
      if ([...failing].some((suffix) => path.endsWith(suffix))) return route.fulfill({ status: 500 });
      return route.fulfill({ json: path.endsWith('/actions/runs') ? { content: [] } : [] });
    }
    if (path.endsWith('/review')) {
      attempts[path] = (attempts[path] ?? 0) + 1;
      return route.fulfill({ status: attempts[path] === 1 ? 500 : 404 });
    }
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/pulls/1');
  await page.getByRole('tab', { name: 'Commits' }).click();
  await expect(page.getByRole('alert')).toContainText('Could not load commits.');
  failing.delete('/commits');
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByRole('alert')).toHaveCount(0);
  await page.getByRole('tab', { name: 'Files changed' }).click();
  await expect(page.getByRole('alert')).toContainText('Could not load changed files.');
  failing.delete('/diff');
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByText('No file changes')).toBeVisible();
  await page.getByRole('tab', { name: 'Actions' }).click();
  await expect(page.getByRole('alert')).toContainText('Could not load workflow runs.');
  failing.delete('/actions/runs');
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByText('No workflow runs')).toBeVisible();
  await page.getByRole('tab', { name: 'AI Review' }).click();
  await expect(page.getByRole('alert')).toContainText('Could not load AI review.');
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByText('No AI review yet')).toBeVisible();
});

test('pull request and comment failures can be retried independently', async ({ page }) => {
  const user = { username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  let prAttempts = 0;
  let commentAttempts = 0;
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/reviewers')) return route.fulfill({ json: [] });
    if (path.endsWith('/reviews')) return route.fulfill({ json: { headSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), reviews: [] } });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: {
      id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false,
    } });
    if (path === '/api/repos/demo/project/pulls/1') {
      if (++prAttempts === 1) return route.fulfill({ status: 500 });
      return route.fulfill({ json: {
        id: 'pr-id', number: 1, title: 'Improve navigation', status: 'OPEN', author: user,
        sourceBranch: 'feature', targetBranch: 'main', isDraft: false, description: null,
        mergedBy: null, createdAt: null,
      } });
    }
    if (path.endsWith('/comments')) {
      if (++commentAttempts <= 2) return route.fulfill({ status: 500 });
      return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    }
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/pulls/1');
  await expect(page.getByRole('alert')).toContainText('Could not load pull request.');
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByRole('heading', { name: 'Improve navigation' })).toBeVisible();
  await expect(page.getByRole('alert')).toContainText('Could not load comments.');
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('changed file disclosure is accessible on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  const user = { username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/reviewers')) return route.fulfill({ json: [] });
    if (path.endsWith('/reviews')) return route.fulfill({ json: { headSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), reviews: [] } });
    if (path === '/api/repo/demo/project') return route.fulfill({ json: {
      id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false,
    } });
    if (path === '/api/repos/demo/project/pulls/1') return route.fulfill({ json: {
      id: 'pr-id', number: 1, title: 'Update file', status: 'OPEN', author: user,
      sourceBranch: 'feature', targetBranch: 'main', isDraft: false, description: null,
      mergedBy: null, createdAt: null,
    } });
    if (path.endsWith('/comments')) return route.fulfill({ json: {
      content: [], number: 0, totalElements: 0, totalPages: 0,
    } });
    if (path.endsWith('/diff')) return route.fulfill({ json: [{
      oldPath: 'example.txt', newPath: 'example.txt', changeType: 'MODIFY', additions: 1, deletions: 0,
      hunks: [{ header: '@@ -0,0 +1 @@', lines: [{ type: 'ADD', oldLineNumber: 0, newLineNumber: 1, content: 'example' }] }],
    }] });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/pulls/1');
  await page.getByRole('tab', { name: 'Files changed' }).click();
  const file = page.getByRole('button', { name: /example.txt/ });
  await expect(file).toHaveAttribute('aria-expanded', 'true');
  await file.click();
  await expect(file).toHaveAttribute('aria-expanded', 'false');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('loading more comments reports errors and preserves existing comments', async ({ page }) => {
  const user = { username: 'demo', name: 'Demo', email: '', avatarUrl: null };
  let moreAttempts = 0;
  await page.route('**/api/**', (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/reviewers')) return route.fulfill({ json: [] });
    if (url.pathname.endsWith('/reviews')) return route.fulfill({ json: { headSha: 'a'.repeat(40), targetSha: 'b'.repeat(40), reviews: [] } });
    if (url.pathname === '/api/repo/demo/project') return route.fulfill({ json: {
      id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false,
    } });
    if (url.pathname === '/api/repos/demo/project/pulls/1') return route.fulfill({ json: {
      id: 'pr-id', number: 1, title: 'Update file', status: 'OPEN', author: user,
      sourceBranch: 'feature', targetBranch: 'main', isDraft: false, description: null,
      mergedBy: null, createdAt: null,
    } });
    if (url.pathname.endsWith('/comments')) {
      const pageNumber = Number(url.searchParams.get('page'));
      if (pageNumber === 1 && ++moreAttempts === 1) return route.fulfill({ status: 500 });
      return route.fulfill({ json: {
        content: [{ id: `comment-${pageNumber}`, author: user, body: `Comment ${pageNumber}`, createdAt: null }],
        number: pageNumber, totalElements: 2, totalPages: 2,
      } });
    }
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/pulls/1');
  await expect(page.getByText('Comment 0')).toBeVisible();
  await page.getByRole('button', { name: 'Load more comments' }).click();
  await expect(page.getByRole('alert')).toContainText('Could not load more comments.');
  await expect(page.getByText('Comment 0')).toBeVisible();
  await page.getByRole('button', { name: 'Load more comments' }).click();
  await expect(page.getByText('Comment 1')).toBeVisible();
});
