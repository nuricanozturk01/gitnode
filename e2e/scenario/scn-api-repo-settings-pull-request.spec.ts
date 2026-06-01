/**
 * Repository settings — PR head-branch delete flags and private repo access (REST).
 * SCN-API-PRSET-01, SCN-API-PRSET-02 — see RAPOR-SCENARIO.md
 */
import { repoApi } from '@helpers/paths';

import { expect, test } from './fixtures/scenario';
import { patchRepoSettings } from './helpers/scenario-api';

test.describe('SCN-API-PRSET — repo settings for PR branch cleanup (API)', () => {
  // SCN-API-PRSET-01 — Owner enables deleteHeadBranchOnPrMerge/Close via PATCH; GET confirms flags.
  test('owner enables merge and close delete-head options via PATCH', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    await patchRepoSettings(bareApi, owner, owner.username, privateRepo.name, {
      name: privateRepo.name,
      deleteHeadBranchOnPrMerge: true,
      deleteHeadBranchOnPrClose: true,
    });

    const response = await bareApi.get(`${repoApi}/${owner.username}/${privateRepo.name}`, {
      headers: { Authorization: owner.authorization },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.deleteHeadBranchOnPrMerge).toBe(true);
    expect(body.deleteHeadBranchOnPrClose).toBe(true);
  });

  // SCN-API-PRSET-02 — Unauthenticated request cannot read private repo metadata (403).
  test('unauthenticated guest cannot read private repo', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const response = await bareApi.get(`${repoApi}/${owner.username}/${privateRepo.name}`);
    expect(response.status()).toBe(403);
  });
});
