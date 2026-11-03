import { expect, test } from './fixtures/scenario';
import {
  addMergeCollaborator,
  createPullRequest,
  prepareFeatureBranch,
  putBlob,
} from './helpers/scenario-api';

test('code owners are requested automatically and must approve before merging', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const codeOwnerHeaders = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await addMergeCollaborator(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    intruder,
  );
  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'main',
    '.github/CODEOWNERS',
    `# everything needs a review\n* @${intruder.username}\n`,
    'Add CODEOWNERS',
  );
  await prepareFeatureBranch(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'owned-change',
  );
  const pr = await createPullRequest(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    { title: 'Owned change', sourceBranch: 'owned-change' },
  );
  const path = `${base}/pulls/${pr.number}`;

  const reviewers = await (await bareApi.get(`${path}/reviewers`, { headers })).json();
  expect(reviewers.map((reviewer: { username: string }) => reviewer.username)).toContain(
    intruder.username,
  );

  expect(
    (
      await bareApi.put(`${base}/branch-protections`, {
        headers,
        data: { branchName: 'main', requiredApprovals: 0, requireCodeOwnerReview: true },
      })
    ).status(),
  ).toBe(200);
  const listed = await (await bareApi.get(`${base}/branch-protections`, { headers })).json();
  expect(listed[0].requireCodeOwnerReview).toBe(true);

  const requirements = () =>
    bareApi.get(`${path}/merge-requirements`, { headers: codeOwnerHeaders }).then((r) => r.json());
  expect(await requirements()).toMatchObject({
    requireCodeOwnerReview: true,
    satisfied: false,
    missingCodeOwners: [{ pattern: '*', owners: [intruder.username] }],
  });

  const blocked = await bareApi.post(`${path}/merge`, {
    headers: codeOwnerHeaders,
    data: { strategy: 'MERGE_COMMIT' },
  });
  expect(blocked.status()).toBe(400);
  expect(await blocked.text()).toContain('Code owner review required');

  const { headSha, targetSha } = await (await bareApi.get(`${path}/reviews`, { headers })).json();
  expect(
    (
      await bareApi.post(`${path}/reviews`, {
        headers: codeOwnerHeaders,
        data: { state: 'APPROVED', body: '', expectedSha: headSha, expectedTargetSha: targetSha },
      })
    ).status(),
  ).toBe(201);
  expect(await requirements()).toMatchObject({ satisfied: true, missingCodeOwners: [] });
  expect(
    (
      await bareApi.post(`${path}/merge`, {
        headers: codeOwnerHeaders,
        data: { strategy: 'MERGE_COMMIT' },
      })
    ).status(),
  ).toBe(200);
});
