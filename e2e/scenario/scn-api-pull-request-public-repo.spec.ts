/**
 * Public repository — pull request REST API.
 * SCN-PUB-PR-01 — see RAPOR-SCENARIO.md
 */
import { expect, test } from './fixtures/scenario';
import { createPullRequest, prepareFeatureBranch } from './helpers/scenario-api';

test.describe('SCN-PUB-PR — public repo pull requests (API)', () => {
  // SCN-PUB-PR-01 — Owner opens a PR on a public repo; GET confirms title, branch, and OPEN.
  test('owner opens pull request via API and GET confirms OPEN', async ({
    bareApi,
    owner,
    publicRepo,
  }) => {
    const branch = `scn-pub-pr-${Date.now().toString(36)}`;
    await prepareFeatureBranch(
      bareApi,
      owner.authorization,
      owner.username,
      publicRepo.name,
      branch,
    );

    const pr = await createPullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      publicRepo.name,
      { title: 'Scenario public PR', sourceBranch: branch },
    );
    expect(pr.number).toBeGreaterThan(0);

    const response = await bareApi.get(
      `/api/repos/${owner.username}/${publicRepo.name}/pulls/${pr.number}`,
      { headers: { Authorization: owner.authorization } },
    );
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.title).toBe('Scenario public PR');
    expect(body.sourceBranch).toBe(branch);
    expect(body.status).toBe('OPEN');
  });
});
