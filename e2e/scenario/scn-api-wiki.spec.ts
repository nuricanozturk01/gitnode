import { expect, test } from './fixtures/scenario';

test('wiki pages are versioned, paged and protected against lost updates', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const outsider = { Authorization: intruder.authorization };
  const pages = `/api/repos/${owner.username}/${publicRepo.name}/wiki/pages`;

  expect((await (await bareApi.get(pages)).json()).content).toEqual([]);

  const created = await bareApi.put(`${pages}/Home`, {
    headers,
    data: { content: '# Welcome', baseSha: null },
  });
  expect(created.status()).toBe(200);
  const home = await created.json();
  expect(home).toMatchObject({
    slug: 'Home',
    content: '# Welcome',
    lastEditor: expect.any(String),
  });

  // Anyone who can read the repository can read the wiki; writing needs push access.
  expect((await bareApi.get(`${pages}/Home`)).status()).toBe(200);
  expect(
    (
      await bareApi.put(`${pages}/Home`, {
        headers: outsider,
        data: { content: 'defaced', baseSha: home.blobSha },
      })
    ).status(),
  ).toBe(403);

  // A save that started from an older version is rejected instead of overwriting.
  const second = await (
    await bareApi.put(`${pages}/Home`, {
      headers,
      data: { content: '# Welcome v2', message: 'second', baseSha: home.blobSha },
    })
  ).json();
  const stale = await bareApi.put(`${pages}/Home`, {
    headers,
    data: { content: 'lost', baseSha: home.blobSha },
  });
  expect(stale.status()).toBe(400);
  expect(await stale.text()).toContain('changed by someone else');
  expect((await (await bareApi.get(`${pages}/Home`)).json()).content).toBe('# Welcome v2');

  const history = await (await bareApi.get(`${pages}/Home/history`)).json();
  expect(history.content.map((r: { message: string }) => r.message)).toEqual([
    'second',
    'Create Home',
  ]);
  const old = await (await bareApi.get(`${pages}/Home?rev=${history.content[1].sha}`)).json();
  expect(old.content).toBe('# Welcome');
  expect(second.blobSha).not.toBe(home.blobSha);

  for (let i = 0; i < 52; i++) {
    const response = await bareApi.put(`${pages}/Page-${String(i).padStart(2, '0')}`, {
      headers,
      data: { content: `page ${i}`, baseSha: null },
    });
    expect(response.status()).toBe(200);
  }
  const firstPage = await (await bareApi.get(pages)).json();
  expect(firstPage).toMatchObject({ size: 50, totalElements: 53, totalPages: 2 });
  expect(firstPage.content[0].slug).toBe('Home');
  expect((await (await bareApi.get(`${pages}?page=1`)).json()).content).toHaveLength(3);

  expect((await bareApi.delete(`${pages}/Page-00`, { headers })).status()).toBe(204);
  expect((await bareApi.get(`${pages}/Page-00`)).status()).toBe(404);

  // The wiki lives on its own ref and must never appear as a branch.
  const branches = await (
    await bareApi.get(`/api/repos/${owner.username}/${publicRepo.name}/branches`, { headers })
  ).text();
  expect(branches).not.toContain('wiki');
});
