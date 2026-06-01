/**
 * Pull request auth, listing, merge impact, and public-repo collaboration.
 * SCN-PRIV-PR-03, SCN-PUB-PR-03, SCN-PRIV-PR-04, SCN-PUB-PR-02 — see RAPOR-SCENARIO.md
 */
import { expect, test } from './fixtures/scenario';
import {
  createPullRequest,
  getPullRequest,
  listCommitMessages,
  listPullRequests,
  mergePullRequest,
  prepareFeatureBranch,
} from './helpers/scenario-api';

test.describe('SCN-PR — pull request access, listing, and merge', () => {
  // SCN-PRIV-PR-03 — Unauthenticated or invalid Bearer cannot create a PR on a private repo.
  test('rejects pull request creation without valid authentication', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const noAuth = await bareApi.post(`/api/repos/${owner.username}/${privateRepo.name}/pulls`, {
      data: {
        title: 'Should fail',
        sourceBranch: 'main',
        targetBranch: 'main',
      },
    });
    expect(noAuth.status()).toBe(401);

    const badToken = await bareApi.post(`/api/repos/${owner.username}/${privateRepo.name}/pulls`, {
      headers: { Authorization: 'Bearer invalid-token-scenario' },
      data: {
        title: 'Should fail',
        sourceBranch: 'main',
        targetBranch: 'main',
      },
    });
    expect([401, 500]).toContain(badToken.status());
    expect(badToken.status()).not.toBe(201);
  });

  // SCN-PUB-PR-03 — Anonymous client can list open pull requests on a public repo.
  test('allows anonymous listing of open pull requests on public repo', async ({
    bareApi,
    owner,
    publicRepo,
  }) => {
    const branch = `scn-pub-list-${Date.now().toString(36)}`;
    await prepareFeatureBranch(
      bareApi,
      owner.authorization,
      owner.username,
      publicRepo.name,
      branch,
    );
    await createPullRequest(bareApi, owner.authorization, owner.username, publicRepo.name, {
      title: 'Public list PR',
      sourceBranch: branch,
    });

    const list = await listPullRequests(bareApi, undefined, owner.username, publicRepo.name);
    expect(list.content.some((p) => p.title === 'Public list PR')).toBe(true);
  });

  // SCN-PRIV-PR-04 — Merging a PR updates main branch commit history on a private repo.
  test('merge pull request adds merge commit on default branch', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const branch = `scn-priv-merge-${Date.now().toString(36)}`;
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
      { title: 'Private merge scenario', sourceBranch: branch, isDraft: false },
    );
    const beforeCount = (
      await listCommitMessages(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        'main',
      )
    ).length;

    await mergePullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      pr.number,
    );

    const merged = await getPullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      pr.number,
    );
    expect(merged.status).toBe('MERGED');

    const messages = await listCommitMessages(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      'main',
    );
    expect(messages.length).toBeGreaterThan(beforeCount);
    expect(messages.some((m) => m.toLowerCase().includes('merge'))).toBe(true);
  });

  // SCN-PUB-PR-02 — Authenticated non-owner can open a PR on a public repo (current API policy).
  test('allows authenticated intruder to open pull request on public repo', async ({
    bareApi,
    owner,
    intruder,
    publicRepo,
  }) => {
    const branch = `scn-pub-intruder-${Date.now().toString(36)}`;
    await prepareFeatureBranch(
      bareApi,
      owner.authorization,
      owner.username,
      publicRepo.name,
      branch,
    );

    const response = await bareApi.post(`/api/repos/${owner.username}/${publicRepo.name}/pulls`, {
      headers: { Authorization: intruder.authorization },
      data: {
        title: 'Intruder public PR',
        description: 'Scenario',
        sourceBranch: branch,
        targetBranch: 'main',
        isDraft: true,
      },
    });
    expect(response.status()).toBe(201);
    expect((await response.json()).number).toBeGreaterThan(0);
  });
});
