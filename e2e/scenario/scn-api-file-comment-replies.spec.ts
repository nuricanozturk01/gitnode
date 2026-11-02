import { expect, test } from './fixtures/scenario';
import { putBlob } from './helpers/scenario-api';

test('file comments take replies and notify the repository owner and thread participants', async ({ bareApi, owner, intruder, publicRepo }) => {
  const ownerAuth = { Authorization: owner.authorization };
  const guestAuth = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}/file-comments`;
  await putBlob(bareApi, owner.authorization, owner.username, publicRepo.name, 'main', 'docs/replies.md', 'one\ntwo\n', 'Add file');
  const notifications = async (auth: Record<string, string>) =>
    ((await (await bareApi.get('/api/notifications?size=50', { headers: auth })).json()).content as { type: string; title: string; link: string }[]).filter((n) => n.type === 'FILE_COMMENT');

  const root = await (await bareApi.post(base, { headers: guestAuth, data: { branch: 'main', path: 'docs/replies.md', line: 2, body: 'Question' } })).json();
  await expect.poll(async () => (await notifications(ownerAuth)).length).toBe(1);
  expect((await notifications(ownerAuth))[0].link).toBe(`/${owner.username}/${publicRepo.name}/blob/main/docs/replies.md`);
  expect(await notifications(guestAuth)).toHaveLength(0);

  const reply = await bareApi.post(base, { headers: ownerAuth, data: { branch: 'main', path: 'docs/replies.md', body: 'Answer', parentId: root.id } });
  expect(reply.status()).toBe(201);
  expect(await reply.json()).toMatchObject({ parentId: root.id, line: 2, path: 'docs/replies.md' });
  await expect.poll(async () => (await notifications(guestAuth)).some((n) => n.title.includes('reply'))).toBe(true);

  const nested = await reply.json();
  expect((await bareApi.post(base, { headers: ownerAuth, data: { branch: 'main', path: 'docs/replies.md', body: 'x', parentId: nested.id } })).status()).toBe(400);
  expect((await bareApi.post(base, { headers: ownerAuth, data: { branch: 'main', path: 'docs/replies.md', body: 'x', parentId: '00000000-0000-0000-0000-000000000001' } })).status()).toBe(400);

  const listed = await (await bareApi.get(`${base}?branch=main&path=${encodeURIComponent('docs/replies.md')}`, { headers: ownerAuth })).json();
  expect(listed.comments.map((c: { parentId: string | null }) => c.parentId)).toEqual([null, root.id]);

  // Deleting the root removes its replies too.
  expect((await bareApi.delete(`${base}/${root.id}`, { headers: guestAuth })).status()).toBe(204);
  const after = await (await bareApi.get(`${base}?branch=main&path=${encodeURIComponent('docs/replies.md')}`, { headers: ownerAuth })).json();
  expect(after.comments).toHaveLength(0);
});
