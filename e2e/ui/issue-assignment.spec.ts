import { expect, test } from '@playwright/test';

test('issue author can assign the issue to themselves and remove the assignee', async ({
  page,
}) => {
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
    email: 'demo@example.org',
    avatarUrl: null,
    name: 'Demo',
  };
  const otherUser = { ...user, id: 'other-id', username: 'other', name: 'Other' };
  let assignee: typeof user | null = null;
  let assignees: (typeof user)[] = [];
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/users/me') return route.fulfill({ json: user });
    if (url.pathname === '/api/users/search') {
      return route.fulfill({
        json: [{ username: 'other', displayName: 'Other', avatarUrl: null }],
      });
    }
    if (url.pathname === '/api/repo/demo/project') {
      return route.fulfill({
        json: {
          id: 'repo-id',
          name: 'project',
          owner: user,
          defaultBranch: 'main',
          isPrivate: false,
        },
      });
    }
    if (url.pathname === '/api/repos/demo/project/issues/1') {
      if (route.request().method() === 'PATCH') {
        const body = route.request().postDataJSON();
        if (body.assigneeId === user.id) assignee = user;
        if (body.assigneeUsername === 'other') assignee = otherUser;
        if (body.addAssigneeUsername) {
          const added = body.addAssigneeUsername === user.username ? user : otherUser;
          assignees = [...assignees.filter((entry) => entry.username !== added.username), added];
          assignee = assignees[0];
        }
        if (body.removeAssigneeUsername) {
          assignees = assignees.filter((entry) => entry.username !== body.removeAssigneeUsername);
          assignee = assignees[0] ?? null;
        }
        if (body.clearAssignee) {
          assignee = null;
          assignees = [];
        }
      }
      return route.fulfill({
        json: {
          id: 'issue-id',
          number: 1,
          title: 'Issue',
          status: 'OPEN',
          author: user,
          assignee,
          assignees,
          commentCount: 0,
        },
      });
    }
    if (url.pathname.endsWith('/comments')) {
      return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
    }
    return route.fulfill({ status: 404 });
  });

  await page.goto('/demo/project/issues/1');
  await page.getByRole('button', { name: 'Assign to me' }).click();
  await expect(page.getByRole('button', { name: 'Remove all assignees' })).toBeVisible();
  await page.getByRole('button', { name: 'Remove all assignees' }).click();
  await expect(page.getByRole('button', { name: 'Remove all assignees' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Assign to me' })).toBeVisible();
  await page.getByLabel('Assignee username').fill('other');
  await expect(page.locator('#assignee-suggestions option')).toHaveAttribute('value', 'other');
  await page.getByRole('button', { name: 'Assign user' }).click();
  await expect(page.getByText('Other', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Assign to me' }).click();
  await expect(page.getByText('Other', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Remove other from assignees' }).click();
  await expect(page.getByText('Other', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Remove all assignees' }).click();
  await expect(page.getByRole('button', { name: 'Assign to me' })).toBeVisible();
});
