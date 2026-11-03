import { createHash } from 'node:crypto';

import { expect, test } from './fixtures/scenario';
import { putBlob } from './helpers/scenario-api';

test('release files are uploaded, downloaded and protected like the release itself', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const guest = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}/releases`;
  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'main',
    'assets.txt',
    '1\n',
    'For assets',
  );
  const make = async (tagName: string, isDraft: boolean) =>
    (await (
      await bareApi.post(base, {
        headers,
        data: { tagName, createNewTag: true, targetCommitish: 'main', isDraft },
      })
    ).json()) as { id: string };
  const published = await make('as-1', false);
  const draft = await make('as-2', true);

  const content = Buffer.from('binary \u0000 payload');
  const upload = (
    releaseId: string,
    name: string,
    buffer: Buffer,
    auth: Record<string, string> = headers,
  ) =>
    bareApi.post(`${base}/${releaseId}/assets`, {
      headers: auth,
      multipart: { file: { name, mimeType: 'application/zip', buffer } },
    });

  expect((await upload(published.id, 'app.zip', content, guest)).status()).toBe(403);
  expect(
    (
      await bareApi.post(`${base}/${published.id}/assets`, {
        multipart: { file: { name: 'x', mimeType: 'text/plain', buffer: content } },
      })
    ).status(),
  ).toBe(401);
  expect((await upload(published.id, 'empty.bin', Buffer.alloc(0))).status()).toBe(400);

  const created = await upload(published.id, '../../evil name?.zip', content);
  expect(created.status()).toBe(201);
  const asset = await created.json();
  expect(asset).toMatchObject({ name: 'evil name_.zip', size: content.length, downloadCount: 0 });
  expect(asset.sha256).toBe(createHash('sha256').update(content).digest('hex'));
  expect((await upload(published.id, 'EVIL name_.ZIP', content)).status()).toBe(400);

  const listed = await (await bareApi.get(`${base}/${published.id}/assets`)).json();
  expect(listed).toHaveLength(1);

  const download = await bareApi.get(`${base}/${published.id}/assets/${asset.id}`);
  expect(download.status()).toBe(200);
  expect(Buffer.from(await download.body()).equals(content)).toBe(true);
  expect(download.headers()['content-disposition']).toContain('attachment');
  expect(download.headers()['x-content-type-options']).toBe('nosniff');
  expect(download.headers()['content-type']).toContain('application/octet-stream');
  expect(
    (await (await bareApi.get(`${base}/${published.id}/assets`)).json())[0].downloadCount,
  ).toBe(1);

  // Files of a draft are visible to maintainers only.
  const draftUpload = await upload(draft.id, 'draft.zip', content);
  expect(draftUpload.status()).toBe(201);
  const draftAsset = await draftUpload.json();
  expect((await bareApi.get(`${base}/${draft.id}/assets`)).status()).toBe(404);
  expect(
    (await bareApi.get(`${base}/${draft.id}/assets/${draftAsset.id}`, { headers: guest })).status(),
  ).toBe(404);
  expect(
    (await bareApi.get(`${base}/${draft.id}/assets/${draftAsset.id}`, { headers })).status(),
  ).toBe(200);

  // A file id from another release is not reachable through this one.
  expect((await bareApi.get(`${base}/${published.id}/assets/${draftAsset.id}`)).status()).toBe(404);

  expect(
    (
      await bareApi.delete(`${base}/${published.id}/assets/${asset.id}`, { headers: guest })
    ).status(),
  ).toBe(403);
  expect(
    (await bareApi.delete(`${base}/${published.id}/assets/${asset.id}`, { headers })).status(),
  ).toBe(204);
  expect(await (await bareApi.get(`${base}/${published.id}/assets`)).json()).toEqual([]);

  // Deleting the release removes its files too.
  expect((await bareApi.delete(`${base}/${draft.id}`, { headers })).status()).toBe(204);
  expect(
    (await bareApi.get(`${base}/${draft.id}/assets/${draftAsset.id}`, { headers })).status(),
  ).toBe(404);
});
