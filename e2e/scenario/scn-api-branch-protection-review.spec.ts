import { expect, test } from './fixtures/scenario';
import {
  addMergeCollaborator,
  createPullRequest,
  prepareFeatureBranch,
  putBlob,
} from './helpers/scenario-api';

test('protected branch requires current approvals and rejects later change requests', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const reviewerHeaders = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await prepareFeatureBranch(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'feature',
  );
  const pr = await createPullRequest(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    { title: 'Protected', sourceBranch: 'feature' },
  );
  const path = `${base}/pulls/${pr.number}`;
  await addMergeCollaborator(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    intruder,
  );
  expect(
    (
      await bareApi.put(`${path}/reviewers`, {
        headers,
        data: { usernames: [owner.username, intruder.username] },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await bareApi.put(`${base}/branch-protections`, {
        headers: reviewerHeaders,
        data: { branchName: 'main', requiredApprovals: 1 },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await bareApi.put(`${base}/branch-protections`, {
        headers,
        data: { branchName: 'main', requiredApprovals: 1 },
      })
    ).status(),
  ).toBe(200);
  const merge = () =>
    bareApi.post(`${path}/merge`, { headers: reviewerHeaders, data: { strategy: 'MERGE_COMMIT' } });
  const review = async (state: string, body = '') => {
    const { headSha, targetSha } = await (await bareApi.get(`${path}/reviews`, { headers })).json();
    return bareApi.post(`${path}/reviews`, {
      headers: reviewerHeaders,
      data: { state, body, expectedSha: headSha, expectedTargetSha: targetSha },
    });
  };
  expect((await merge()).status()).toBe(400);
  expect(await (await bareApi.get(`${path}/merge-requirements`, { headers })).json()).toMatchObject(
    { requiredApprovals: 1, currentApprovals: 0, satisfied: false },
  );
  expect((await review('APPROVED')).status()).toBe(201);
  expect(await (await bareApi.get(`${path}/merge-requirements`, { headers })).json()).toMatchObject(
    { currentApprovals: 1, changesRequestedBy: [], satisfied: true },
  );
  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'feature',
    'new.txt',
    'updated',
    'Update feature',
  );
  expect((await merge()).status()).toBe(400);
  expect((await review('APPROVED')).status()).toBe(201);
  expect((await review('CHANGES_REQUESTED', 'Please update')).status()).toBe(201);
  expect(
    (await (await bareApi.get(`${path}/merge-requirements`, { headers })).json())
      .changesRequestedBy,
  ).toEqual([intruder.username]);
  expect((await merge()).status()).toBe(400);
  expect((await review('APPROVED')).status()).toBe(201);
  expect((await merge()).status()).toBe(200);
});
