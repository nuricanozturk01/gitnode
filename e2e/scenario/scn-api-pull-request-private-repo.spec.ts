/**
 * Private repository — pull request REST API.
 * SCN-PRIV-PR-01, SCN-PRIV-PR-02 — see RAPOR-SCENARIO.md
 */
import { expect, test } from './fixtures/scenario';
import { createPullRequest, prepareFeatureBranch } from './helpers/scenario-api';

test.describe('SCN-PRIV-PR — private repo pull requests (API)', () => {
  // SCN-PRIV-PR-01 — Owner opens a PR on a private repo; GET confirms OPEN and source branch.
  test('owner opens pull request via API', async ({ bareApi, owner, privateRepo }) => {
    const branch = `scn-priv-pr-${Date.now().toString(36)}`;
    await prepareFeatureBranch(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branch,
    );

    const pr = await createPullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      { title: 'Scenario private PR', sourceBranch: branch },
    );

    const response = await bareApi.get(
      `/api/repos/${owner.username}/${privateRepo.name}/pulls/${pr.number}`,
      { headers: { Authorization: owner.authorization } },
    );
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.sourceBranch).toBe(branch);
    expect(body.status).toBe('OPEN');
  });

  // SCN-PRIV-PR-02 — Intruder cannot create a PR on someone else's private repo (4xx).
  test('intruder cannot open pull request on private repo', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }) => {
    const response = await bareApi.post(`/api/repos/${owner.username}/${privateRepo.name}/pulls`, {
      headers: { Authorization: intruder.authorization },
      data: {
        title: 'Intruder PR',
        sourceBranch: 'main',
        targetBranch: 'main',
      },
    });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });
});
