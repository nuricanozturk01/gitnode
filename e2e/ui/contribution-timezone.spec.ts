import { expect, test } from '@playwright/test';

test.use({ timezoneId: 'America/Los_Angeles', locale: 'en-US' });

test('contribution dates and month labels use the UTC calendar', async ({ page }) => {
  const breakdown = {
    issues: 1, issueComments: 0, pullRequests: 0, pullRequestComments: 0,
    pullRequestMerges: 0, releases: 0, snippets: 0, snippetRevisions: 0, snippetComments: 0,
  };
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/users/demo/contributions') {
      return route.fulfill({ json: {
        username: 'demo', rangeStart: '2026-01-01', rangeEnd: '2026-02-01',
        totalContributions: 1, longestStreak: 1, currentStreak: 0,
        includesPrivateActivity: false,
        days: [{ date: '2026-01-01', count: 1, level: 1, breakdown }],
      } });
    }
    if (path === '/api/users/demo') {
      return route.fulfill({ json: { username: 'demo', displayName: 'Demo', avatarUrl: null } });
    }
    return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0 } });
  });

  await page.goto('/demo');
  const cell = page.getByRole('button', { name: 'Thu, Jan 1, 2026: 1 contribution' });
  await expect(cell).toBeVisible();
  await cell.focus();
  await expect(page.getByRole('tooltip')).toContainText('Thu, Jan 1, 2026');
  await expect(page.locator('.contrib-graph__month', { hasText: 'Feb' })).toBeVisible();
});
