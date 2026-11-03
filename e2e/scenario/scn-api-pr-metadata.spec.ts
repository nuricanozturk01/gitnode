import { expect, test } from './fixtures/scenario';
import { createPullRequest, prepareFeatureBranch } from './helpers/scenario-api';

test('pull requests carry labels, assignees and a milestone with proper permissions', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
  privateRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const guest = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await prepareFeatureBranch(bareApi, owner.authorization, owner.username, publicRepo.name, 'meta');
  const pr = await createPullRequest(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    { title: 'Metadata', sourceBranch: 'meta' },
  );
  const path = `${base}/pulls/${pr.number}/metadata`;

  const label = async (repo: string, name: string, color: string) =>
    (await (
      await bareApi.post(`/api/repos/${owner.username}/${repo}/labels`, {
        headers,
        data: { name, color, description: '' },
      })
    ).json()) as { id: string };
  const bug = await label(publicRepo.name, 'bug', 'd73a4a');
  const docs = await label(publicRepo.name, 'docs', '0075ca');
  const foreign = await label(privateRepo.name, 'foreign', '000000');
  const milestone = (await (
    await bareApi.post(`${base}/milestones`, {
      headers,
      data: { title: 'v1', description: '', status: 'OPEN' },
    })
  ).json()) as { id: string };

  const empty = await (await bareApi.get(path, { headers })).json();
  expect(empty).toMatchObject({ labels: [], assignees: [], milestone: null, canEdit: true });
  expect((await (await bareApi.get(path, { headers: guest })).json()).canEdit).toBe(false);

  const put = (data: object, auth = headers) => bareApi.put(path, { headers: auth, data });
  expect(
    (await put({ labelIds: [bug.id], assignees: [], milestoneId: null }, guest)).status(),
  ).toBe(403);
  expect(
    (
      await bareApi.put(path, { data: { labelIds: [], assignees: [], milestoneId: null } })
    ).status(),
  ).toBe(401);
  expect((await put({ labelIds: [foreign.id], assignees: [], milestoneId: null })).status()).toBe(
    400,
  );
  expect(
    (await put({ labelIds: [], assignees: [intruder.username], milestoneId: null })).status(),
  ).toBe(400);
  expect(
    (await put({ labelIds: [], assignees: ['nobody-here'], milestoneId: null })).status(),
  ).toBe(404);
  expect(
    (
      await put({
        labelIds: [],
        assignees: [],
        milestoneId: '00000000-0000-0000-0000-000000000000',
      })
    ).status(),
  ).toBe(400);

  const saved = await put({
    labelIds: [docs.id, bug.id],
    assignees: [owner.username],
    milestoneId: milestone.id,
  });
  expect(saved.status()).toBe(200);
  const body = await saved.json();
  expect(body.labels.map((l: { name: string }) => l.name)).toEqual(['bug', 'docs']);
  expect(body.assignees.map((a: { username: string }) => a.username)).toEqual([owner.username]);
  expect(body.milestone).toMatchObject({ title: 'v1', status: 'OPEN' });
  expect(await (await bareApi.get(path)).json()).toMatchObject({
    canEdit: false,
    milestone: { title: 'v1' },
  });

  const cleared = await (
    await put({ labelIds: [bug.id], assignees: [], milestoneId: null })
  ).json();
  expect(cleared.labels).toHaveLength(1);
  expect(cleared.milestone).toBeNull();
});
