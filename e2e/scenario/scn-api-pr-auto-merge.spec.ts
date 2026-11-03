import { expect, test } from './fixtures/scenario';
import {
  addMergeCollaborator,
  createPullRequest,
  prepareFeatureBranch,
  waitForPullRequestStatus,
} from './helpers/scenario-api';

test('auto-merge waits for the branch rules and then merges without a manual click', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  // The scheduler retries auto-merge every 15 seconds.
  test.setTimeout(120_000);
  const headers = { Authorization: owner.authorization };
  const reviewerHeaders = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await addMergeCollaborator(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    intruder,
  );
  await prepareFeatureBranch(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'auto-merge',
  );
  const pr = await createPullRequest(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    { title: 'Auto merge', sourceBranch: 'auto-merge' },
  );
  const path = `${base}/pulls/${pr.number}`;
  expect(
    (
      await bareApi.put(`${path}/reviewers`, {
        headers,
        data: { usernames: [owner.username, intruder.username] },
      })
    ).status(),
  ).toBe(200);

  const autoMerge = (strategy = 'SQUASH') =>
    bareApi.put(`${path}/auto-merge`, { headers, data: { strategy } });

  // No protection yet: the pull request can simply be merged.
  expect((await autoMerge()).status()).toBe(400);

  expect(
    (
      await bareApi.put(`${base}/branch-protections`, {
        headers,
        data: { branchName: 'main', requiredApprovals: 1 },
      })
    ).status(),
  ).toBe(200);

  const enabled = await autoMerge();
  expect(enabled.status()).toBe(200);
  expect(await enabled.json()).toMatchObject({
    satisfied: false,
    autoMergeStrategy: 'SQUASH',
    autoMergeBy: owner.username,
  });

  expect((await (await bareApi.get(path, { headers })).json()).status).toBe('OPEN');

  const disabled = await bareApi.delete(`${path}/auto-merge`, { headers });
  expect(disabled.status()).toBe(200);
  expect((await disabled.json()).autoMergeStrategy).toBeNull();
  expect((await autoMerge('REBASE')).status()).toBe(200);

  const { headSha, targetSha } = await (await bareApi.get(`${path}/reviews`, { headers })).json();
  expect(
    (
      await bareApi.post(`${path}/reviews`, {
        headers: reviewerHeaders,
        data: { state: 'APPROVED', body: '', expectedSha: headSha, expectedTargetSha: targetSha },
      })
    ).status(),
  ).toBe(201);

  await waitForPullRequestStatus(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    pr.number,
    'MERGED',
  );
  const merged = await (await bareApi.get(path, { headers })).json();
  expect(merged.mergedBy.username).toBe(owner.username);
});
