import { expect, test } from './fixtures/scenario';
import {
  addMergeCollaborator,
  createPullRequest,
  prepareFeatureBranch,
} from './helpers/scenario-api';

test('repository owner can bypass branch protection while collaborators cannot', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const collaboratorHeaders = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await prepareFeatureBranch(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'owner-bypass',
  );
  const pr = await createPullRequest(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    { title: 'Owner bypass', sourceBranch: 'owner-bypass' },
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
        headers,
        data: { branchName: 'main', requiredApprovals: 0, requiredChecks: ['ci'] },
      })
    ).status(),
  ).toBe(200);

  const requirements = (h: { Authorization: string }) =>
    bareApi.get(`${path}/merge-requirements`, { headers: h }).then((r) => r.json());
  expect(await requirements(headers)).toMatchObject({ satisfied: false, canBypass: true });
  expect(await requirements(collaboratorHeaders)).toMatchObject({
    satisfied: false,
    canBypass: false,
  });

  const blocked = await bareApi.post(`${path}/merge`, {
    headers: collaboratorHeaders,
    data: { strategy: 'MERGE_COMMIT' },
  });
  expect(blocked.status()).toBe(400);
  expect(await blocked.text()).toContain('Required checks have not passed: ci');

  expect(
    (await bareApi.post(`${path}/merge`, { headers, data: { strategy: 'MERGE_COMMIT' } })).status(),
  ).toBe(200);
});
