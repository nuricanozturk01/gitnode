import { expect, test } from './fixtures/scenario';
import { createBranch, createPullRequest, putBlob } from './helpers/scenario-api';

test('conflicting PR is blocked, resolved through the API and then merges', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'main',
    'conflict.txt',
    'one\ntwo\nthree\n',
    'Add file',
  );
  await createBranch(bareApi, owner.authorization, owner.username, publicRepo.name, 'conflicting');
  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'conflicting',
    'conflict.txt',
    'one\nFEATURE\nthree\n',
    'Feature edit',
  );
  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'main',
    'conflict.txt',
    'one\nMAIN\nthree\n',
    'Main edit',
  );
  const pr = await createPullRequest(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    { title: 'Conflicting', sourceBranch: 'conflicting' },
  );
  const path = `${base}/pulls/${pr.number}`;
  expect(
    (
      await bareApi.put(`${path}/reviewers`, { headers, data: { usernames: [owner.username] } })
    ).status(),
  ).toBe(200);

  const state = await (await bareApi.get(`${path}/conflicts`, { headers })).json();
  expect(state.mergeable).toBe(false);
  expect(state.files).toHaveLength(1);
  expect(state.files[0]).toMatchObject({ path: 'conflict.txt', resolvable: true });
  expect(state.files[0].content).toContain('<<<<<<<');
  expect(
    (await bareApi.post(`${path}/merge`, { headers, data: { strategy: 'MERGE_COMMIT' } })).status(),
  ).not.toBe(200);

  const form = (content: string, sourceSha = state.sourceSha) => ({
    sourceSha,
    targetSha: state.targetSha,
    files: [{ path: 'conflict.txt', content }],
  });
  expect(
    (
      await bareApi.post(`${path}/conflicts/resolve`, {
        headers: { Authorization: intruder.authorization },
        data: form('x\n'),
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await bareApi.post(`${path}/conflicts/resolve`, {
        headers,
        data: form(state.files[0].content),
      })
    ).status(),
  ).toBe(400);
  expect(
    (
      await bareApi.post(`${path}/conflicts/resolve`, {
        headers,
        data: form('x\n', 'f'.repeat(40)),
      })
    ).status(),
  ).toBe(400);

  const resolved = await bareApi.post(`${path}/conflicts/resolve`, {
    headers,
    data: form('one\nBOTH\nthree\n'),
  });
  expect(resolved.status()).toBe(200);
  expect((await resolved.json()).commitSha).toMatch(/^[0-9a-f]{40}$/);
  expect((await (await bareApi.get(`${path}/conflicts`, { headers })).json()).mergeable).toBe(true);
  expect(
    (await bareApi.post(`${path}/merge`, { headers, data: { strategy: 'MERGE_COMMIT' } })).status(),
  ).toBe(200);
  const raw = await bareApi.get(`${base}/raw/main/conflict.txt`, { headers });
  expect(await raw.text()).toBe('one\nBOTH\nthree\n');
});
