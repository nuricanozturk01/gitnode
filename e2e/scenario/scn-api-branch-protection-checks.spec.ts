import { expect, test } from './fixtures/scenario';
import { createPullRequest, prepareFeatureBranch } from './helpers/scenario-api';

test('required workflows block merging until they succeed', async ({
  bareApi,
  owner,
  publicRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await prepareFeatureBranch(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'needs-ci',
  );
  const pr = await createPullRequest(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    { title: 'Needs CI', sourceBranch: 'needs-ci' },
  );
  const path = `${base}/pulls/${pr.number}`;
  expect(
    (
      await bareApi.put(`${path}/reviewers`, { headers, data: { usernames: [owner.username] } })
    ).status(),
  ).toBe(200);

  const invalid = await bareApi.put(`${base}/branch-protections`, {
    headers,
    data: { branchName: 'main', requiredApprovals: 0, requiredChecks: ['a,b'] },
  });
  expect(invalid.status()).toBe(400);

  const saved = await bareApi.put(`${base}/branch-protections`, {
    headers,
    data: { branchName: 'main', requiredApprovals: 0, requiredChecks: [' ci ', 'ci', 'lint'] },
  });
  expect(saved.status()).toBe(200);
  const rule = await saved.json();
  expect(rule.requiredChecks).toEqual(['ci', 'lint']);

  const blocked = await bareApi.post(`${path}/merge`, {
    headers,
    data: { strategy: 'MERGE_COMMIT' },
  });
  expect(blocked.status()).toBe(400);
  expect(await blocked.text()).toContain('Required checks have not passed: ci, lint');

  const requirements = await (await bareApi.get(`${path}/merge-requirements`, { headers })).json();
  expect(requirements).toMatchObject({
    protectedBranch: true,
    requiredApprovals: 0,
    satisfied: false,
    checks: [
      { name: 'ci', state: 'MISSING' },
      { name: 'lint', state: 'MISSING' },
    ],
  });

  const listed = await (await bareApi.get(`${base}/branch-protections`, { headers })).json();
  expect(listed[0].requiredChecks).toEqual(['ci', 'lint']);

  expect(
    (
      await bareApi.put(`${base}/branch-protections`, {
        headers,
        data: { branchName: 'main', requiredApprovals: 0, requiredChecks: [] },
      })
    ).status(),
  ).toBe(200);
  expect(
    (await (await bareApi.get(`${path}/merge-requirements`, { headers })).json()).satisfied,
  ).toBe(true);
  expect(
    (await bareApi.post(`${path}/merge`, { headers, data: { strategy: 'MERGE_COMMIT' } })).status(),
  ).toBe(200);
});
