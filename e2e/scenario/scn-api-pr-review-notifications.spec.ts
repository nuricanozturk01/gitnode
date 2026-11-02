import { expect, test } from './fixtures/scenario';
import { createPullRequest, prepareFeatureBranch } from './helpers/scenario-api';

test('reviewers are notified of requests and authors of submitted reviews', async ({ bareApi, owner, intruder, publicRepo }) => {
  const headers = { Authorization: owner.authorization };
  const reviewerHeaders = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await prepareFeatureBranch(bareApi, owner.authorization, owner.username, publicRepo.name, 'notify-review');
  const pr = await createPullRequest(bareApi, owner.authorization, owner.username, publicRepo.name, { title: 'Notify', sourceBranch: 'notify-review' });
  const path = `${base}/pulls/${pr.number}`;
  expect((await bareApi.post(`${base}/collaborators`, { headers, data: { username: intruder.username, permissions: ['READ', 'PULL_REQUEST_REVIEW'] } })).status()).toBe(201);
  expect((await bareApi.post(`${base}/collaborators/invitation/accept`, { headers: reviewerHeaders })).status()).toBe(200);
  expect((await bareApi.put(`${path}/reviewers`, { headers, data: { usernames: [owner.username, intruder.username] } })).status()).toBe(200);

  const titles = async (auth: Record<string, string>) =>
    ((await (await bareApi.get('/api/notifications?size=50', { headers: auth })).json()).content as { type: string; title: string; link: string }[]);

  await expect.poll(async () => (await titles(reviewerHeaders)).some((n) => n.type === 'PR_REVIEW_REQUESTED' && n.link.endsWith(`/pulls/${pr.number}`))).toBe(true);
  expect((await titles(headers)).some((n) => n.type === 'PR_REVIEW_REQUESTED')).toBe(false);

  const { headSha, targetSha } = await (await bareApi.get(`${path}/reviews`, { headers })).json();
  expect((await bareApi.post(`${path}/reviews`, { headers: reviewerHeaders, data: { state: 'APPROVED', body: '', expectedSha: headSha, expectedTargetSha: targetSha } })).status()).toBe(201);
  await expect.poll(async () => (await titles(headers)).some((n) => n.type === 'PR_REVIEW_SUBMITTED' && n.title.includes('approved'))).toBe(true);
});
