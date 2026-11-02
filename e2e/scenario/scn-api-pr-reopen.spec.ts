import { expect, test } from './fixtures/scenario';
import { closePullRequest, createPullRequest, prepareFeatureBranch } from './helpers/scenario-api';

test('a closed pull request can be reopened by an authorized user only once', async ({ bareApi, owner, intruder, publicRepo }) => {
  const headers = { Authorization: owner.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await prepareFeatureBranch(bareApi, owner.authorization, owner.username, publicRepo.name, 'reopen-me');
  const pr = await createPullRequest(bareApi, owner.authorization, owner.username, publicRepo.name, { title: 'Reopen', sourceBranch: 'reopen-me' });
  const path = `${base}/pulls/${pr.number}`;

  expect((await bareApi.post(`${path}/reopen`, { headers })).status()).toBe(400);
  await closePullRequest(bareApi, owner.authorization, owner.username, publicRepo.name, pr.number);
  expect((await bareApi.post(`${path}/reopen`, { headers: { Authorization: intruder.authorization } })).status()).toBe(403);

  const reopened = await bareApi.post(`${path}/reopen`, { headers });
  expect(reopened.status()).toBe(200);
  expect((await reopened.json()).status).toBe('OPEN');
  expect((await bareApi.post(`${path}/reopen`, { headers })).status()).toBe(400);

  await closePullRequest(bareApi, owner.authorization, owner.username, publicRepo.name, pr.number);
  const duplicate = await createPullRequest(bareApi, owner.authorization, owner.username, publicRepo.name, { title: 'Replacement', sourceBranch: 'reopen-me' });
  expect(duplicate.number).not.toBe(pr.number);
  const blocked = await bareApi.post(`${path}/reopen`, { headers });
  expect(blocked.status()).toBe(400);
  expect(await blocked.text()).toContain('Another open pull request');
});
