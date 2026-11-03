import { expect, test } from './fixtures/scenario';
import { createPullRequest, prepareFeatureBranch } from './helpers/scenario-api';

test('pull request list shows labels, assignees, milestone and filters by them', async ({
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
    'list-a',
  );
  await prepareFeatureBranch(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'list-b',
  );
  const first = await createPullRequest(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    { title: 'First', sourceBranch: 'list-a' },
  );
  const second = await createPullRequest(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    { title: 'Second', sourceBranch: 'list-b' },
  );

  const bug = (await (
    await bareApi.post(`${base}/labels`, {
      headers,
      data: { name: 'bug', color: 'd73a4a', description: '' },
    })
  ).json()) as { id: string };
  const milestone = (await (
    await bareApi.post(`${base}/milestones`, {
      headers,
      data: { title: 'v1', description: '', status: 'OPEN' },
    })
  ).json()) as { id: string };
  const put = await bareApi.put(`${base}/pulls/${first.number}/metadata`, {
    headers,
    data: { labelIds: [bug.id], assignees: [owner.username], milestoneId: milestone.id },
  });
  expect(put.status()).toBe(200);

  const list = async (query = '') =>
    (await (await bareApi.get(`${base}/pulls?status=OPEN${query}`, { headers })).json()) as {
      totalElements: number;
      content: {
        number: number;
        labels: { name: string }[];
        assignees: { username: string }[];
        milestone: { title: string } | null;
      }[];
    };

  const all = await list();
  expect(all.totalElements).toBe(2);
  const row = all.content.find((p) => p.number === first.number)!;
  expect(row.labels.map((l) => l.name)).toEqual(['bug']);
  expect(row.assignees.map((a) => a.username)).toEqual([owner.username]);
  expect(row.milestone?.title).toBe('v1');
  const other = all.content.find((p) => p.number === second.number)!;
  expect(other).toMatchObject({ labels: [], assignees: [], milestone: null });

  for (const query of [
    `&labelId=${bug.id}`,
    `&milestoneId=${milestone.id}`,
    `&assignee=${owner.username.toUpperCase()}`,
    `&labelId=${bug.id}&milestoneId=${milestone.id}&assignee=${owner.username}`,
  ]) {
    const filtered = await list(query);
    expect(
      filtered.content.map((p) => p.number),
      query,
    ).toEqual([first.number]);
    expect(filtered.totalElements).toBe(1);
  }
  expect((await list('&assignee=nobody-here')).totalElements).toBe(0);
  expect((await list('&labelId=00000000-0000-0000-0000-000000000001')).totalElements).toBe(0);
});
