import { expect, test } from './fixtures/scenario';
import {
  addMergeCollaborator,
  createPullRequest,
  prepareFeatureBranch,
} from './helpers/scenario-api';

test('protected branch can require every review conversation to be resolved before merging', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const mergerHeaders = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await prepareFeatureBranch(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'convo',
  );
  const pr = await createPullRequest(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    { title: 'Conversations', sourceBranch: 'convo' },
  );
  const path = `${base}/pulls/${pr.number}`;
  await addMergeCollaborator(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    intruder,
  );
  expect(
    (
      await bareApi.put(`${path}/reviewers`, {
        headers,
        data: { usernames: [owner.username, intruder.username] },
      })
    ).status(),
  ).toBe(200);

  const rule = await bareApi.put(`${base}/branch-protections`, {
    headers,
    data: { branchName: 'main', requiredApprovals: 0, requireConversationResolution: true },
  });
  expect(rule.status()).toBe(200);
  expect((await rule.json()).requireConversationResolution).toBe(true);

  const line = await (
    await bareApi.post(`${path}/comments`, {
      headers,
      data: {
        body: 'Rename this',
        filePath: 'scenario/convo.txt',
        lineNumber: 1,
        lineSide: 'RIGHT',
      },
    })
  ).json();
  await bareApi.post(`${path}/comments`, {
    headers,
    data: { body: 'A general remark does not block' },
  });
  const requirements = () =>
    bareApi.get(`${path}/merge-requirements`, { headers }).then((r) => r.json());
  expect(await requirements()).toMatchObject({
    requireConversationResolution: true,
    unresolvedConversations: 1,
    satisfied: false,
  });
  const blocked = await bareApi.post(`${path}/merge`, {
    headers: mergerHeaders,
    data: { strategy: 'MERGE_COMMIT' },
  });
  expect(blocked.status()).toBe(400);
  expect(await blocked.text()).toContain('conversations');

  expect(
    (
      await bareApi.put(`${path}/comments/${line.id}/resolved`, {
        headers,
        data: { resolved: true },
      })
    ).status(),
  ).toBe(200);
  expect(await requirements()).toMatchObject({ unresolvedConversations: 0, satisfied: true });
  expect(
    (
      await bareApi.post(`${path}/merge`, {
        headers: mergerHeaders,
        data: { strategy: 'MERGE_COMMIT' },
      })
    ).status(),
  ).toBe(200);
});
