import { expect, test } from '@playwright/test';

test('milestones can be created, filtered and assigned on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-token');
    localStorage.setItem('refresh_token', 'test-refresh');
    localStorage.setItem('expires_at', String(Date.now() + 60_000));
    localStorage.setItem('refresh_expires_at', String(Date.now() + 60_000));
    localStorage.setItem('username', 'demo');
  });
  const user = { id: 'user-id', username: 'demo', email: 'demo@example.org', avatarUrl: null, name: 'Demo' };
  let milestones: { id: string; title: string; description: string | null; dueDate: string | null; status: string }[] = [];
  let assigned: typeof milestones[number] | null = null;
  let rejectAssignment = true;
  let lastFilter = '';
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname === '/api/users/me') return route.fulfill({ json: user });
    if (url.pathname === '/api/repo/demo/project') return route.fulfill({ json: {
      id: 'repo-id', name: 'project', owner: user, defaultBranch: 'main', isPrivate: false,
    } });
    if (url.pathname === '/api/repos/demo/project/milestones') {
      if (request.method() === 'POST') milestones = [{ id: 'milestone-id', ...request.postDataJSON() }];
      return route.fulfill({ json: request.method() === 'POST' ? milestones[0] : milestones });
    }
    if (url.pathname === '/api/repos/demo/project/labels') return route.fulfill({ json: [] });
    if (url.pathname === '/api/repos/demo/project/issues') {
      lastFilter = url.searchParams.get('milestoneId') ?? '';
      return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    }
    if (url.pathname === '/api/repos/demo/project/issues/1') {
      if (request.method() === 'PATCH') {
        const body = request.postDataJSON();
        if (body.milestoneId && rejectAssignment) {
          rejectAssignment = false;
          return route.fulfill({ status: 500 });
        }
        assigned = body.milestoneId ? milestones.find((item) => item.id === body.milestoneId) ?? null : null;
      }
      return route.fulfill({ json: {
        id: 'issue-id', number: 1, title: 'Issue', status: 'OPEN', author: user,
        assignee: null, assignees: [], labels: [], milestone: assigned, commentCount: 0,
      } });
    }
    if (url.pathname.endsWith('/comments')) return route.fulfill({ json: {
      content: [], number: 0, totalElements: 0, totalPages: 0,
    } });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/demo/project/issues');
  await page.getByText('Manage milestones').click();
  await page.getByLabel('Title', { exact: true }).fill('First release');
  await page.getByLabel('Due date').fill('2027-01-01');
  await page.getByRole('button', { name: 'Create milestone' }).click();
  await expect(page.getByRole('option', { name: 'First release (OPEN)' })).toHaveCount(1);
  await page.getByLabel('Filter by milestone').selectOption('milestone-id');
  await expect.poll(() => lastFilter).toBe('milestone-id');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.goto('/demo/project/issues/1');
  await page.getByLabel('Issue milestone').selectOption('milestone-id');
  await expect(page.getByLabel('Issue milestone')).toHaveValue('');
  await page.getByLabel('Issue milestone').selectOption('milestone-id');
  await expect(page.getByLabel('Issue milestone')).toHaveValue('milestone-id');
  await page.getByLabel('Issue milestone').selectOption('');
  await expect(page.getByLabel('Issue milestone')).toHaveValue('');
});
