import { expect, test } from './fixtures/scenario';
import { createPullRequest, prepareFeatureBranch } from './helpers/scenario-api';

test('a pull request can be converted to draft, cannot merge, then merges when ready', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await prepareFeatureBranch(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'draft-toggle',
  );
  const pr = await createPullRequest(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    { title: 'Draft toggle', sourceBranch: 'draft-toggle' },
  );
  const path = `${base}/pulls/${pr.number}`;
  expect(
    (
      await bareApi.put(`${path}/reviewers`, { headers, data: { usernames: [owner.username] } })
    ).status(),
  ).toBe(200);

  const drafted = await bareApi.patch(path, { headers, data: { isDraft: true } });
  expect(drafted.status()).toBe(200);
  expect((await drafted.json()).isDraft).toBe(true);
  expect(
    (await bareApi.post(`${path}/merge`, { headers, data: { strategy: 'MERGE_COMMIT' } })).status(),
  ).toBe(400);
  expect(
    (
      await bareApi.patch(path, {
        headers: { Authorization: intruder.authorization },
        data: { isDraft: false },
      })
    ).status(),
  ).toBe(403);

  const ready = await bareApi.patch(path, { headers, data: { isDraft: false } });
  expect((await ready.json()).isDraft).toBe(false);
  expect(
    (await bareApi.post(`${path}/merge`, { headers, data: { strategy: 'MERGE_COMMIT' } })).status(),
  ).toBe(200);
});
