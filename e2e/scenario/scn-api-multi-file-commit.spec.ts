import { expect, test } from './fixtures/scenario';
import { putBlob } from './helpers/scenario-api';

test('several file changes are committed atomically against the branch head', async ({ bareApi, owner, intruder, publicRepo }) => {
  const headers = { Authorization: owner.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await putBlob(bareApi, owner.authorization, owner.username, publicRepo.name, 'main', 'editor/one.txt', 'one\n', 'Add one');
  await putBlob(bareApi, owner.authorization, owner.username, publicRepo.name, 'main', 'editor/two.txt', 'two\n', 'Add two');
  const headOf = async (branch: string) => {
    const branches = (await (await bareApi.get(`${base}/branches`, { headers })).json()) as { name: string; lastCommitSha: string }[];
    return branches.find((b) => b.name === branch)!.lastCommitSha;
  };
  const head = await headOf('main');
  const changes = [
    { path: 'editor/one.txt', content: 'one edited\n', delete: false },
    { path: 'editor/three/new.txt', content: 'brand new\n', delete: false },
    { path: 'editor/two.txt', delete: true },
  ];
  const body = (expectedHeadSha: string, list = changes) => ({ branch: 'main', expectedHeadSha, message: 'Edit in the web editor', description: 'Three changes', changes: list });

  expect((await bareApi.post(`${base}/changes`, { data: body(head) })).status()).toBe(401);
  expect((await bareApi.post(`${base}/changes`, { headers: { Authorization: intruder.authorization }, data: body(head) })).status()).toBe(403);
  expect((await bareApi.post(`${base}/changes`, { headers, data: body(head, [{ path: '../evil', content: 'x', delete: false }]) })).status()).toBe(400);
  expect((await bareApi.post(`${base}/changes`, { headers, data: body('f'.repeat(40)) })).status()).toBe(400);
  expect(await headOf('main')).toBe(head);

  const committed = await bareApi.post(`${base}/changes`, { headers, data: body(head) });
  expect(committed.status()).toBe(200);
  const { commitSha } = await committed.json();
  expect(commitSha).toMatch(/^[0-9a-f]{40}$/);
  expect(await headOf('main')).toBe(commitSha);

  const read = async (path: string) => bareApi.get(`${base}/raw/main/${path}`, { headers });
  expect(await (await read('editor/one.txt')).text()).toBe('one edited\n');
  expect(await (await read('editor/three/new.txt')).text()).toBe('brand new\n');
  expect((await read('editor/two.txt')).status()).toBe(404);

  expect((await bareApi.post(`${base}/changes`, { headers, data: body(head) })).status()).toBe(400);

  expect((await bareApi.put(`${base}/branch-protections`, { headers, data: { branchName: 'main', requiredApprovals: 0, requiredChecks: [] } })).status()).toBe(200);
  const protectedAttempt = await bareApi.post(`${base}/changes`, { headers, data: body(commitSha, [{ path: 'editor/blocked.txt', content: 'x', delete: false }]) });
  expect(protectedAttempt.status()).toBe(400);
});
