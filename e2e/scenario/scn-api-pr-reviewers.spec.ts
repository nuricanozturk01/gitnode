import { expect, test } from './fixtures/scenario';
import { createPullRequest, prepareFeatureBranch, putBlob } from './helpers/scenario-api';

test('reviewer assignment enforces current merge permission', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const intruderHeaders = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  const collaborators = `${base}/collaborators`;
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
    { title: 'Review', sourceBranch: 'feature' },
  );
  const path = `${base}/pulls/${pr.number}`;
  const merge = () =>
    bareApi.post(`${path}/merge`, { headers, data: { strategy: 'MERGE_COMMIT' } });
  expect((await merge()).status()).toBe(400);
  expect(
    (
      await bareApi.put(`${path}/reviewers`, {
        headers: intruderHeaders,
        data: { usernames: [owner.username] },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await bareApi.post(collaborators, {
        headers,
        data: { username: intruder.username, permissions: ['READ', 'PULL_REQUEST_MERGE'] },
      })
    ).status(),
  ).toBe(201);
  expect(
    (
      await bareApi.post(`${collaborators}/invitation/accept`, { headers: intruderHeaders })
    ).status(),
  ).toBe(200);
  const assigned = await bareApi.put(`${path}/reviewers`, {
    headers,
    data: { usernames: [intruder.username] },
  });
  expect(assigned.status()).toBe(200);
  expect((await assigned.json())[0].canMerge).toBe(true);
  expect(
    (
      await bareApi.put(`${collaborators}/${intruder.username}/permissions`, {
        headers,
        data: { permissions: ['READ', 'PULL_REQUEST_REVIEW'] },
      })
    ).status(),
  ).toBe(200);
  expect((await merge()).status()).toBe(400);
  expect(
    (
      await bareApi.put(`${path}/reviewers`, { headers, data: { usernames: [intruder.username] } })
    ).status(),
  ).toBe(400);
  expect(
    (
      await bareApi.put(`${path}/reviewers`, {
        headers,
        data: { usernames: [owner.username, intruder.username] },
      })
    ).status(),
  ).toBe(200);
  const reviews = await bareApi.get(`${path}/reviews`);
  expect(reviews.status()).toBe(200);
  const { headSha, targetSha } = (await reviews.json()) as { headSha: string; targetSha: string };
  expect(
    (
      await bareApi.post(`${path}/reviews`, {
        headers,
        data: { state: 'APPROVED', expectedSha: headSha, expectedTargetSha: targetSha },
      })
    ).status(),
  ).toBe(400);
  expect(
    (
      await bareApi.post(`${path}/reviews`, {
        headers: intruderHeaders,
        data: { state: 'APPROVED', expectedSha: '0'.repeat(40), expectedTargetSha: targetSha },
      })
    ).status(),
  ).toBe(400);
  const review = await bareApi.post(`${path}/reviews`, {
    headers: intruderHeaders,
    data: { state: 'APPROVED', expectedSha: headSha, expectedTargetSha: targetSha },
  });
  expect(review.status()).toBe(201);
  expect((await review.json()).commitSha).toBe(headSha);
  expect((await (await bareApi.get(`${path}/reviews`)).json()).reviews).toHaveLength(1);
  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'feature',
    'new.txt',
    'new change',
    'Advance branch',
  );
  expect(
    (
      await bareApi.post(`${path}/reviews`, {
        headers: intruderHeaders,
        data: { state: 'APPROVED', expectedSha: headSha, expectedTargetSha: targetSha },
      })
    ).status(),
  ).toBe(400);
  const latest = (await (await bareApi.get(`${path}/reviews`)).json()) as { headSha: string };
  expect(latest.headSha).not.toBe(headSha);
  expect(
    (
      await bareApi.post(`${path}/reviews`, {
        headers: intruderHeaders,
        data: { state: 'APPROVED', expectedSha: latest.headSha, expectedTargetSha: targetSha },
      })
    ).status(),
  ).toBe(201);
  expect((await merge()).status()).toBe(200);
});
