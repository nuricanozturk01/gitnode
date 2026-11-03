import { expect, test } from './fixtures/scenario';
import { putBlob } from './helpers/scenario-api';

test('release notes list the commits since the previous tag', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'main',
    'notes.txt',
    'one\n',
    'Start notes file',
  );
  expect(
    (
      await bareApi.post(`${base}/releases`, {
        headers,
        data: { tagName: 'n1', createNewTag: true, targetCommitish: 'main' },
      })
    ).status(),
  ).toBe(201);
  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'main',
    'notes.txt',
    'two\n',
    'Add the second line',
  );
  await putBlob(
    bareApi,
    owner.authorization,
    owner.username,
    publicRepo.name,
    'main',
    'notes.txt',
    'three\n',
    'Rewrite to three',
  );

  const generated = await bareApi.get(
    `${base}/releases/generate-notes?tagName=n2&targetCommitish=main`,
    { headers },
  );
  expect(generated.status()).toBe(200);
  const notes = await generated.json();
  expect(notes.previousTag).toBe('n1');
  expect(notes.commitCount).toBe(2);
  expect(notes.body).toContain('Add the second line');
  expect(notes.body).toContain('Rewrite to three');
  expect(notes.body).not.toContain('Start notes file');
  expect(notes.body).toContain('**Full Changelog**: `n1...n2`');

  expect(
    (
      await bareApi.get(`${base}/releases/generate-notes?tagName=n2&targetCommitish=nope`, {
        headers,
      })
    ).status(),
  ).toBe(400);
  expect(
    (
      await bareApi.get(`${base}/releases/generate-notes?tagName=n2&targetCommitish=main`, {
        headers: { Authorization: intruder.authorization },
      })
    ).status(),
  ).toBe(403);
});
