import { expect, test } from './fixtures/scenario';
import { createPullRequest, prepareFeatureBranch } from './helpers/scenario-api';

test('line conversations take replies and metadata changes notify the right people', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const other = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await prepareFeatureBranch(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'replies',
  );
  const pr = await createPullRequest(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    { title: 'Replies', sourceBranch: 'replies' },
  );
  const path = `${base}/pulls/${pr.number}`;
  expect(
    (
      await bareApi.post(`${base}/collaborators`, {
        headers,
        data: { username: intruder.username, permissions: ['READ', 'PULL_REQUEST_REVIEW'] },
      })
    ).status(),
  ).toBe(201);
  expect(
    (await bareApi.post(`${base}/collaborators/invitation/accept`, { headers: other })).status(),
  ).toBe(200);
  const comments = `${path}/comments`;

  const root = await (
    await bareApi.post(comments, {
      headers,
      data: { body: 'Rename', filePath: 'scenario/replies.txt', lineNumber: 1, lineSide: 'RIGHT' },
    })
  ).json();
  const general = await (
    await bareApi.post(comments, { headers, data: { body: 'General' } })
  ).json();

  const reply = await bareApi.post(comments, {
    headers: other,
    data: { body: 'Done', parentId: root.id },
  });
  expect(reply.status()).toBe(201);
  const replyBody = await reply.json();
  expect(replyBody).toMatchObject({
    parentId: root.id,
    filePath: 'scenario/replies.txt',
    lineNumber: 1,
    lineSide: 'RIGHT',
  });
  expect(
    (
      await bareApi.post(comments, { headers, data: { body: 'x', parentId: replyBody.id } })
    ).status(),
  ).toBe(400);
  expect(
    (await bareApi.post(comments, { headers, data: { body: 'x', parentId: general.id } })).status(),
  ).toBe(400);
  expect(
    (
      await bareApi.post(comments, {
        headers,
        data: { body: 'x', parentId: '00000000-0000-0000-0000-000000000001' },
      })
    ).status(),
  ).toBe(400);
  expect(
    (
      await bareApi.put(`${comments}/${replyBody.id}/resolved`, {
        headers,
        data: { resolved: true },
      })
    ).status(),
  ).toBe(400);

  const notifications = async (auth: Record<string, string>) =>
    (await (await bareApi.get('/api/notifications?size=50', { headers: auth })).json()).content as {
      type: string;
    }[];
  await expect
    .poll(async () => (await notifications(headers)).some((n) => n.type === 'PR_COMMENT'))
    .toBe(true);

  const label = (await (
    await bareApi.post(`${base}/labels`, {
      headers,
      data: { name: 'ui', color: '0075ca', description: '' },
    })
  ).json()) as { id: string };
  expect(
    (
      await bareApi.put(`${path}/metadata`, {
        headers,
        data: { labelIds: [], assignees: [intruder.username], milestoneId: null },
      })
    ).status(),
  ).toBe(200);
  await expect
    .poll(async () => (await notifications(other)).some((n) => n.type === 'PR_ASSIGNED'))
    .toBe(true);
  expect((await notifications(headers)).some((n) => n.type === 'PR_ASSIGNED')).toBe(false);

  expect(
    (
      await bareApi.put(`${path}/metadata`, {
        headers,
        data: { labelIds: [label.id], assignees: [intruder.username], milestoneId: null },
      })
    ).status(),
  ).toBe(200);
  await expect
    .poll(async () => (await notifications(other)).some((n) => n.type === 'PR_METADATA_CHANGED'))
    .toBe(true);
});
