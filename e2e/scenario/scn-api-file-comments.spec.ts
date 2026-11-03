import { expect, test } from './fixtures/scenario';
import { putBlob } from './helpers/scenario-api';

test('file comments are anchored to commits, permissioned and survive file changes', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
  privateRepo,
}) => {
  const ownerAuth = { Authorization: owner.authorization };
  const guestAuth = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}/file-comments`;
  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'main',
    'docs/guide.md',
    'one\ntwo\nthree\n',
    'Add guide',
  );
  const query = `branch=main&path=${encodeURIComponent('docs/guide.md')}`;

  expect(
    (
      await bareApi.post(base, { data: { branch: 'main', path: 'docs/guide.md', body: 'anon' } })
    ).status(),
  ).toBe(401);

  const line = await bareApi.post(base, {
    headers: guestAuth,
    data: { branch: 'main', path: 'docs/guide.md', line: 2, body: 'Why "two"?' },
  });
  expect(line.status()).toBe(201);
  const created = await line.json();
  expect(created).toMatchObject({
    line: 2,
    outdated: false,
    canEdit: true,
    canDelete: true,
    author: { username: intruder.username },
  });

  expect(
    (
      await bareApi.post(base, {
        headers: guestAuth,
        data: { branch: 'main', path: 'docs/guide.md', line: 99, body: 'x' },
      })
    ).status(),
  ).toBe(400);
  expect(
    (
      await bareApi.post(base, {
        headers: guestAuth,
        data: { branch: 'main', path: 'missing.md', body: 'x' },
      })
    ).status(),
  ).toBe(404);
  expect(
    (
      await bareApi.post(base, {
        headers: guestAuth,
        data: { branch: 'main', path: '../secret', body: 'x' },
      })
    ).status(),
  ).toBe(400);
  expect(
    (
      await bareApi.post(base, {
        headers: guestAuth,
        data: { branch: 'main', path: 'docs/guide.md', body: '   ' },
      })
    ).status(),
  ).toBe(400);

  const asOwner = await (await bareApi.get(`${base}?${query}`, { headers: ownerAuth })).json();
  expect(asOwner.comments).toHaveLength(1);
  expect(asOwner.comments[0]).toMatchObject({ canEdit: false, canDelete: true });
  const anonymous = await (await bareApi.get(`${base}?${query}`)).json();
  expect(anonymous.comments[0]).toMatchObject({ canEdit: false, canDelete: false });

  expect(
    (
      await bareApi.patch(`${base}/${created.id}`, { headers: ownerAuth, data: { body: 'hijack' } })
    ).status(),
  ).toBe(403);
  const edited = await bareApi.patch(`${base}/${created.id}`, {
    headers: guestAuth,
    data: { body: 'Edited' },
  });
  expect(edited.status()).toBe(200);
  expect((await edited.json()).updatedAt).toBeTruthy();

  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'main',
    'docs/guide.md',
    'one\ntwo\nthree\nfour\n',
    'Extend guide',
  );
  const second = await bareApi.post(base, {
    headers: ownerAuth,
    data: { branch: 'main', path: 'docs/guide.md', line: 4, body: 'New line' },
  });
  expect(second.status()).toBe(201);
  const afterChange = await (await bareApi.get(`${base}?${query}`, { headers: ownerAuth })).json();
  expect(afterChange.comments.map((c: { outdated: boolean }) => c.outdated)).toEqual([true, false]);

  expect(
    (
      await bareApi.delete(`${base}/${created.id}`, {
        headers: { Authorization: owner.authorization },
      })
    ).status(),
  ).toBe(204);
  expect(
    (await (await bareApi.get(`${base}?${query}`, { headers: ownerAuth })).json()).comments,
  ).toHaveLength(1);

  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    privateRepo.name,
    'main',
    'secret.txt',
    's\n',
    'Add secret',
  );
  const privateBase = `/api/repos/${owner.username}/${privateRepo.name}/file-comments`;
  const denied = await bareApi.post(privateBase, {
    headers: guestAuth,
    data: { branch: 'main', path: 'secret.txt', body: 'peek' },
  });
  expect([403, 404]).toContain(denied.status());
  expect([401, 403, 404]).toContain(
    (await bareApi.get(`${privateBase}?branch=main&path=secret.txt`)).status(),
  );
});
