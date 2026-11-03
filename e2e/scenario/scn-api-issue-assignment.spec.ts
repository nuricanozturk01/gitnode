import { expect, test } from './fixtures/scenario';
import { createScenarioIssue } from './helpers/issue-api';

test('issue assignment can be changed and removed only by an authorized user', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const me = await bareApi.get('/api/users/me', { headers });
  expect(me.status()).toBe(200);
  const { id } = (await me.json()) as { id: string };
  const issue = await createScenarioIssue(
    bareApi,
    owner,
    owner.username,
    publicRepo.name,
    'Assignment',
  );
  const path = `/api/repos/${owner.username}/${publicRepo.name}/issues/${issue.number}`;

  const assigned = await bareApi.patch(path, { headers, data: { assigneeId: id } });
  expect(assigned.status()).toBe(200);
  expect((await assigned.json()).assignee.username).toBe(owner.username);

  const rejected = await bareApi.patch(path, {
    headers: { Authorization: intruder.authorization },
    data: { clearAssignee: true },
  });
  expect(rejected.status()).toBe(403);

  const unchanged = await bareApi.patch(path, { headers, data: { title: 'Still assigned' } });
  expect((await unchanged.json()).assignee.username).toBe(owner.username);
  const assignedByUsername = await bareApi.patch(path, {
    headers,
    data: { assigneeUsername: intruder.username },
  });
  expect(assignedByUsername.status()).toBe(200);
  expect((await assignedByUsername.json()).assignee.username).toBe(intruder.username);
  const addOwner = await bareApi.patch(path, {
    headers,
    data: { addAssigneeUsername: owner.username },
  });
  expect(addOwner.status()).toBe(200);
  expect(
    (await addOwner.json()).assignees.map((user: { username: string }) => user.username).sort(),
  ).toEqual([intruder.username, owner.username].sort());
  const reread = await bareApi.get(path, { headers });
  expect(
    (await reread.json()).assignees.map((user: { username: string }) => user.username).sort(),
  ).toEqual([intruder.username, owner.username].sort());
  const removeIntruder = await bareApi.patch(path, {
    headers,
    data: { removeAssigneeUsername: intruder.username },
  });
  expect(removeIntruder.status()).toBe(200);
  expect(
    (await removeIntruder.json()).assignees.map((user: { username: string }) => user.username),
  ).toEqual([owner.username]);
  const conflictingUsername = await bareApi.patch(path, {
    headers,
    data: { assigneeId: id, assigneeUsername: intruder.username },
  });
  expect(conflictingUsername.status()).toBe(400);
  const conflicting = await bareApi.patch(path, {
    headers,
    data: { assigneeId: id, clearAssignee: true },
  });
  expect(conflicting.status()).toBe(400);
  const cleared = await bareApi.patch(path, { headers, data: { clearAssignee: true } });
  expect(cleared.status()).toBe(200);
  expect((await cleared.json()).assignee).toBeNull();
  const concurrent = await Promise.all(
    Array.from({ length: 8 }, (_, index) =>
      bareApi.patch(path, {
        headers,
        data: { addAssigneeUsername: index % 2 === 0 ? owner.username : intruder.username },
      }),
    ),
  );
  expect(concurrent.map((response) => response.status())).toEqual(Array(8).fill(200));
  const afterConcurrent = await bareApi.get(path, { headers });
  expect(
    (await afterConcurrent.json()).assignees
      .map((user: { username: string }) => user.username)
      .sort(),
  ).toEqual([intruder.username, owner.username].sort());
});
