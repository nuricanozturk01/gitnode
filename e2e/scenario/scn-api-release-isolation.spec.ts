import { expect, test } from './fixtures/scenario';
import { createScenarioRepo } from './helpers/scenario-api';

test('release and tag operations respect repository boundaries and write access', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
  privateRepo,
}) => {
  const ownerHeaders = { Authorization: owner.authorization };
  const intruderHeaders = { Authorization: intruder.authorization };
  const victim = `/api/repos/${owner.username}/${privateRepo.name}`;
  const publicVictim = `/api/repos/${owner.username}/${publicRepo.name}`;
  const attackerRepo = await createScenarioRepo(bareApi, intruder, { isPrivate: false });
  const attacker = `/api/repos/${intruder.username}/${attackerRepo.name}`;
  const created = await bareApi.post(`${victim}/releases`, {
    headers: ownerHeaders,
    data: { tagName: 'v1', name: 'Private release', createNewTag: true },
  });
  expect(created.status()).toBe(201);
  const release = (await created.json()) as { id: string };
  expect(
    (
      await bareApi.get(`${attacker}/releases/${release.id}`, { headers: intruderHeaders })
    ).status(),
  ).toBe(404);
  expect(
    (
      await bareApi.patch(`${attacker}/releases/${release.id}`, {
        headers: intruderHeaders,
        data: { name: 'Tampered' },
      })
    ).status(),
  ).toBe(404);
  expect(
    (
      await bareApi.delete(`${attacker}/releases/${release.id}`, { headers: intruderHeaders })
    ).status(),
  ).toBe(404);
  expect(
    (
      await bareApi.post(`${publicVictim}/tags`, {
        headers: intruderHeaders,
        data: { name: 'unwanted' },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await bareApi.post(`${publicVictim}/releases`, {
        headers: intruderHeaders,
        data: { tagName: 'unwanted', createNewTag: true },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await bareApi.post(`${publicVictim}/tags`, {
        headers: ownerHeaders,
        data: { name: 'protected' },
      })
    ).status(),
  ).toBe(201);
  expect(
    (await bareApi.delete(`${publicVictim}/tags/protected`, { headers: intruderHeaders })).status(),
  ).toBe(403);
  expect(
    (await bareApi.delete(`${publicVictim}/tags/protected`, { headers: ownerHeaders })).status(),
  ).toBe(204);
  expect(
    (await bareApi.get(`${victim}/releases/${release.id}`, { headers: ownerHeaders })).status(),
  ).toBe(200);
  expect(
    (
      await bareApi.patch(`${victim}/releases/${release.id}`, {
        headers: ownerHeaders,
        data: { name: 'Updated release' },
      })
    ).status(),
  ).toBe(200);
  expect(
    (await bareApi.delete(`${victim}/releases/${release.id}`, { headers: ownerHeaders })).status(),
  ).toBe(204);
  const draftResponse = await bareApi.post(`${publicVictim}/releases`, {
    headers: ownerHeaders,
    data: { tagName: 'draft-tag', name: 'Secret draft', isDraft: true, createNewTag: true },
  });
  expect(draftResponse.status()).toBe(201);
  const draft = (await draftResponse.json()) as { id: string; isDraft: boolean };
  expect(draft.isDraft).toBe(true);
  const publicReleases = await bareApi.get(`${publicVictim}/releases`);
  expect((await publicReleases.json()).map((entry: { id: string }) => entry.id)).not.toContain(
    draft.id,
  );
  expect((await bareApi.get(`${publicVictim}/releases/${draft.id}`)).status()).toBe(404);
  expect((await bareApi.get(`${publicVictim}/releases/tag/draft-tag`)).status()).toBe(404);
  const publicTags = await bareApi.get(`${publicVictim}/tags`);
  expect(
    (await publicTags.json()).find((entry: { name: string }) => entry.name === 'draft-tag').release,
  ).toBeNull();
  const publicTag = await bareApi.get(`${publicVictim}/tags/draft-tag`);
  expect((await publicTag.json()).release).toBeNull();
  const intruderReleases = await bareApi.get(`${publicVictim}/releases`, {
    headers: intruderHeaders,
  });
  expect((await intruderReleases.json()).map((entry: { id: string }) => entry.id)).not.toContain(
    draft.id,
  );
  const ownerDraft = await bareApi.get(`${publicVictim}/releases/${draft.id}`, {
    headers: ownerHeaders,
  });
  expect(ownerDraft.status()).toBe(200);
  const ownerTag = await bareApi.get(`${publicVictim}/tags/draft-tag`, { headers: ownerHeaders });
  expect((await ownerTag.json()).release.id).toBe(draft.id);
});
