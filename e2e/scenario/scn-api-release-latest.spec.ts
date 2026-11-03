import { expect, test } from './fixtures/scenario';
import { putBlob } from './helpers/scenario-api';

test('a release can be pinned as latest, and the pin follows the rules', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}/releases`;
  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'main',
    'rel.txt',
    '1\n',
    'For releases',
  );
  const make = async (tagName: string, extra: object = {}) => {
    const res = await bareApi.post(base, {
      headers,
      data: { tagName, createNewTag: true, targetCommitish: 'main', ...extra },
    });
    expect(res.status()).toBe(201);
    return (await res.json()) as { id: string };
  };
  const first = await make('lt-1');
  await new Promise((r) => setTimeout(r, 1100));
  const second = await make('lt-2');
  const draft = await make('lt-3', { isDraft: true });
  const pre = await make('lt-4', { isPrerelease: true });

  const latest = async () =>
    (await (await bareApi.get(`${base}/latest`, { headers })).json()) as {
      id: string;
      isLatest: boolean;
    };
  expect((await latest()).id).toBe(second.id);

  expect(
    (
      await bareApi.post(`${base}/${first.id}/latest`, {
        headers: { Authorization: intruder.authorization },
      })
    ).status(),
  ).toBe(403);
  expect((await bareApi.post(`${base}/${draft.id}/latest`, { headers })).status()).toBe(400);
  expect((await bareApi.post(`${base}/${pre.id}/latest`, { headers })).status()).toBe(400);

  const pinned = await bareApi.post(`${base}/${first.id}/latest`, { headers });
  expect(pinned.status()).toBe(200);
  expect((await pinned.json()).isLatest).toBe(true);
  expect(await latest()).toMatchObject({ id: first.id, isLatest: true });

  const list = (await (await bareApi.get(base, { headers })).json()) as {
    id: string;
    isLatest: boolean;
  }[];
  expect(list.filter((r) => r.isLatest).map((r) => r.id)).toEqual([first.id]);

  // Re-pinning moves the flag instead of creating a second one.
  expect((await bareApi.post(`${base}/${second.id}/latest`, { headers })).status()).toBe(200);
  expect((await latest()).id).toBe(second.id);

  // A pin that stops being stable falls back to the newest stable release.
  expect(
    (
      await bareApi.patch(`${base}/${second.id}`, { headers, data: { isPrerelease: true } })
    ).status(),
  ).toBe(200);
  expect((await latest()).id).toBe(first.id);
});
