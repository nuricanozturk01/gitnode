import { expect, test } from './fixtures/scenario';
import {
  createPullRequest,
  createScenarioRepo,
  prepareFeatureBranch,
} from './helpers/scenario-api';

test('comment mutations stay within their pull request and require the author', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  const otherRepo = await createScenarioRepo(bareApi, owner, { isPrivate: false });
  const paths: string[] = [];

  for (const repo of [publicRepo, otherRepo]) {
    await prepareFeatureBranch(bareApi, owner.authorization, owner.username, repo.name, 'feature');
    const pr = await createPullRequest(bareApi, owner.authorization, owner.username, repo.name, {
      title: 'Comment isolation',
      sourceBranch: 'feature',
    });
    paths.push(`/api/repos/${owner.username}/${repo.name}/pulls/${pr.number}`);
  }

  const ownerHeaders = { Authorization: owner.authorization };
  const intruderHeaders = { Authorization: intruder.authorization };
  const created = await bareApi.post(`${paths[0]}/comments`, {
    headers: ownerHeaders,
    data: { body: 'Original comment' },
  });
  expect(created.status()).toBe(201);
  const { id } = (await created.json()) as { id: string };

  const wrongRepo = `${paths[1]}/comments/${id}`;
  expect(
    (await bareApi.patch(wrongRepo, { headers: ownerHeaders, data: { body: 'Wrong' } })).status(),
  ).toBe(404);
  expect((await bareApi.delete(wrongRepo, { headers: ownerHeaders })).status()).toBe(404);

  const comment = `${paths[0]}/comments/${id}`;
  expect(
    (await bareApi.patch(comment, { headers: intruderHeaders, data: { body: 'Wrong' } })).status(),
  ).toBe(403);
  expect((await bareApi.delete(comment, { headers: intruderHeaders })).status()).toBe(403);
  expect(
    (await bareApi.patch(comment, { headers: ownerHeaders, data: { body: 'Updated' } })).status(),
  ).toBe(200);
  expect((await bareApi.delete(comment, { headers: ownerHeaders })).status()).toBe(204);
});

test('only authorized users can change a pull request, and drafts cannot merge', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  await prepareFeatureBranch(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'draft',
  );
  const pr = await createPullRequest(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    {
      title: 'Draft pull request',
      sourceBranch: 'draft',
      isDraft: true,
    },
  );
  const path = `/api/repos/${owner.username}/${publicRepo.name}/pulls/${pr.number}`;
  const ownerHeaders = { Authorization: owner.authorization };
  const intruderHeaders = { Authorization: intruder.authorization };

  expect(
    (await bareApi.patch(path, { headers: intruderHeaders, data: { title: 'Changed' } })).status(),
  ).toBe(403);
  expect((await bareApi.delete(path, { headers: intruderHeaders })).status()).toBe(403);
  const merge = await bareApi.post(`${path}/merge`, {
    headers: ownerHeaders,
    data: { strategy: 'MERGE_COMMIT', commitMessage: 'Merge draft' },
  });
  expect(merge.status()).toBe(400);
  const detail = await bareApi.get(path, { headers: ownerHeaders });
  expect(detail.status()).toBe(200);
  expect((await detail.json()).status).toBe('OPEN');
});
