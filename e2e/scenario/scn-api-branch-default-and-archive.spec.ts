/**
 * Default branch change and branch archive download.
 * SCN-API-BRANCH-01, SCN-API-ARCHIVE-01 — see RAPOR-SCENARIO.md
 */
import { reposApi } from '@helpers/paths';

import { expect, test } from './fixtures/scenario';
import { createBranch, getRepo, putBlob, setDefaultBranch } from './helpers/scenario-api';

test.describe('SCN-API — branch default and archive', () => {
  // SCN-API-BRANCH-01 — Owner can set default branch; GET repo reflects the new default.
  test('updates default branch on the repository', async ({ bareApi, owner, privateRepo }) => {
    const develop = `scn-develop-${Date.now().toString(36)}`;
    await createBranch(bareApi, owner.authorization, owner.username, privateRepo.name, develop);
    await putBlob(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      develop,
      'on-develop.txt',
      'develop content\n',
      'scenario: develop commit',
    );

    await setDefaultBranch(bareApi, owner.authorization, owner.username, privateRepo.name, develop);

    const repo = await getRepo(bareApi, owner.authorization, owner.username, privateRepo.name);
    expect(repo.defaultBranch).toBe(develop);
  });

  // SCN-API-ARCHIVE-01 — Branch archive endpoint returns a ZIP for main.
  test('downloads branch archive as application/zip', async ({ bareApi, owner, privateRepo }) => {
    const response = await bareApi.get(
      `${reposApi(owner.username, privateRepo.name)}/archive/main`,
      { headers: { Authorization: owner.authorization } },
    );
    expect(response.status()).toBe(200);
    const contentType = response.headers()['content-type'] ?? '';
    expect(contentType).toContain('zip');
    const body = await response.body();
    expect(body.byteLength).toBeGreaterThan(100);
  });
});
