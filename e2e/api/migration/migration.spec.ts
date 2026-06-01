import { expect, test } from '../fixtures/authenticated-api';
import { migrationApi } from '@helpers/paths';

test.describe('Migration API — all endpoints', () => {
  test('GET /api/migration/{jobId} returns 404 for unknown job', async ({
    authedRequest,
  }) => {
    const response = await authedRequest.get(
      `${migrationApi}/00000000-0000-0000-0000-000000000000`,
    );
    expect(response.status()).toBe(404);
  });

  test('POST /api/migration rejects invalid payload', async ({ authedRequest, api }) => {
    const response = await authedRequest.post(migrationApi, {
      data: {
        service: 'GITHUB',
        url: 'not-a-github-url',
        accessToken: 'fake',
        migrationItems: ['REPOSITORIES'],
        owner: api.owner,
        repoName: 'invalid-import',
      },
    });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });
});
