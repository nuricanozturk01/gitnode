import { expect, test } from './fixtures/scenario';

test('tree reads enforce privacy and file writes require push permission', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
  privateRepo,
}) => {
  const ownerHeaders = { Authorization: owner.authorization };
  const intruderHeaders = { Authorization: intruder.authorization };
  const privateBase = `/api/repos/${owner.username}/${privateRepo.name}`;
  const publicBase = `/api/repos/${owner.username}/${publicRepo.name}`;
  for (const suffix of ['/tree/main', '/blob/main/README.md', '/raw/main/README.md']) {
    expect((await bareApi.get(`${privateBase}${suffix}`)).status()).toBe(403);
    expect(
      (await bareApi.get(`${privateBase}${suffix}`, { headers: intruderHeaders })).status(),
    ).toBe(403);
    expect((await bareApi.get(`${privateBase}${suffix}`, { headers: ownerHeaders })).status()).toBe(
      200,
    );
    expect((await bareApi.get(`${publicBase}${suffix}`)).status()).toBe(200);
  }
  const update = { content: 'unauthorized change', commitMessage: 'edit' };
  expect(
    (
      await bareApi.put(`${publicBase}/blob/main/README.md`, {
        headers: intruderHeaders,
        data: update,
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await bareApi.put(`${privateBase}/blob/main/README.md`, {
        headers: intruderHeaders,
        data: update,
      })
    ).status(),
  ).toBe(403);
  const original = await bareApi.get(`${publicBase}/raw/main/README.md`);
  expect(await original.text()).not.toBe(update.content);
  expect(
    (
      await bareApi.put(`${publicBase}/blob/main/README.md`, {
        headers: ownerHeaders,
        data: { content: 'owner change', commitMessage: 'edit' },
      })
    ).status(),
  ).toBe(200);
  const branch = { name: 'protected-test', sourceBranch: 'main' };
  expect(
    (
      await bareApi.post(`${publicBase}/branches`, { headers: intruderHeaders, data: branch })
    ).status(),
  ).toBe(403);
  expect(
    (
      await bareApi.post(`${publicBase}/branches`, { headers: ownerHeaders, data: branch })
    ).status(),
  ).toBe(201);
  expect(
    (
      await bareApi.delete(`${publicBase}/branches/protected-test`, { headers: intruderHeaders })
    ).status(),
  ).toBe(403);
  expect(
    (
      await bareApi.patch(`${publicBase}/branches/default`, {
        headers: intruderHeaders,
        data: { branchName: 'protected-test' },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await bareApi.delete(`${publicBase}/branches/protected-test`, { headers: ownerHeaders })
    ).status(),
  ).toBe(204);
  expect(
    (
      await bareApi.post(`${publicBase}/collaborators`, {
        headers: ownerHeaders,
        data: { username: intruder.username, permissions: ['READ', 'PUSH'] },
      })
    ).status(),
  ).toBe(201);
  expect(
    (
      await bareApi.post(`${publicBase}/collaborators/invitation/accept`, {
        headers: intruderHeaders,
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await bareApi.post(`${publicBase}/branches`, { headers: intruderHeaders, data: branch })
    ).status(),
  ).toBe(201);
  expect(
    (
      await bareApi.put(`${publicBase}/blob/main/README.md`, {
        headers: intruderHeaders,
        data: { content: 'collaborator change', commitMessage: 'edit' },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await bareApi.patch(`${publicBase}/branches/default`, {
        headers: intruderHeaders,
        data: { branchName: 'protected-test' },
      })
    ).status(),
  ).toBe(403);
  expect(
    (
      await bareApi.delete(`${publicBase}/branches/protected-test`, { headers: intruderHeaders })
    ).status(),
  ).toBe(204);
});
