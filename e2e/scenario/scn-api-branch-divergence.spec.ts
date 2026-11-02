import { expect, test } from './fixtures/scenario';
import { createBranch, putBlob } from './helpers/scenario-api';

test('branch list reports how far each branch is ahead of and behind the default branch', async ({ bareApi, owner, publicRepo }) => {
  const headers = { Authorization: owner.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await createBranch(bareApi, owner.authorization, owner.username, publicRepo.name, 'diverged');
  await createBranch(bareApi, owner.authorization, owner.username, publicRepo.name, 'untouched');
  await putBlob(bareApi, owner.authorization, owner.username, publicRepo.name, 'diverged', 'd/one.txt', '1\n', 'Diverged one');
  await putBlob(bareApi, owner.authorization, owner.username, publicRepo.name, 'diverged', 'd/two.txt', '2\n', 'Diverged two');
  await putBlob(bareApi, owner.authorization, owner.username, publicRepo.name, 'main', 'm/main.txt', 'm\n', 'Main moved on');

  const list = (await (await bareApi.get(`${base}/branches`, { headers })).json()) as { name: string; ahead: number; behind: number; isDefault: boolean }[];
  const by = (name: string) => list.find((b) => b.name === name)!;
  expect(by('diverged')).toMatchObject({ ahead: 2, behind: 1 });
  expect(by('untouched')).toMatchObject({ ahead: 0, behind: 1 });
  expect(by('main')).toMatchObject({ isDefault: true, ahead: 0, behind: 0 });
});
