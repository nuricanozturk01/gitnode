import { E2E_PASSWORD } from '@helpers/test-user';

import { expect, test } from './fixtures/scenario';
import { createBranch, createScenarioRepo, putBlob } from './helpers/scenario-api';
import type { ScenarioUser } from './helpers/types';

interface Contributions {
  totalContributions: number;
  includesPrivateActivity: boolean;
  days: { count: number; breakdown: { commits: number; pullRequestReviews: number; fileComments: number } }[];
}

test('commits to the default branch count in the contribution heatmap and respect privacy', async ({ bareApi, intruder }) => {
  // A dedicated account keeps this count independent of the other scenarios running in parallel.
  const username = `heat_${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
  const registered = await bareApi.post('/api/auth/register', { data: { username, email: `${username}@example.org`, password: E2E_PASSWORD } });
  expect(registered.status()).toBe(200);
  const login = (await registered.json()) as ScenarioUser;
  const owner: ScenarioUser = { ...login, username, email: `${username}@example.org`, password: E2E_PASSWORD, authorization: `Bearer ${login.token}` };
  const publicRepo = await createScenarioRepo(bareApi, owner, { isPrivate: false });
  const privateRepo = await createScenarioRepo(bareApi, owner, { isPrivate: true });

  const url = `/api/users/${owner.username}/contributions`;
  const read = async (authorization?: string) =>
    (await (await bareApi.get(url, { headers: authorization ? { Authorization: authorization } : {} })).json()) as Contributions;
  const commits = (c: Contributions) => c.days.reduce((sum, day) => sum + day.breakdown.commits, 0);

  // Each repository was seeded with one README commit by this account.
  await expect.poll(async () => commits(await read(owner.authorization)), { timeout: 20_000 }).toBe(2);
  expect(commits(await read())).toBe(1);

  await putBlob(bareApi, owner.authorization, owner.username, publicRepo.name, 'main', 'heat/one.txt', '1\n', 'Heat one');
  await putBlob(bareApi, owner.authorization, owner.username, publicRepo.name, 'main', 'heat/two.txt', '2\n', 'Heat two');
  await putBlob(bareApi, owner.authorization, owner.username, privateRepo.name, 'main', 'heat/secret.txt', 's\n', 'Heat secret');
  // Commits on a non-default branch do not count.
  await createBranch(bareApi, owner.authorization, owner.username, publicRepo.name, 'heat-side');
  await putBlob(bareApi, owner.authorization, owner.username, publicRepo.name, 'heat-side', 'heat/side.txt', 'x\n', 'Side only');

  await expect.poll(async () => commits(await read(owner.authorization)), { timeout: 20_000 }).toBe(5);
  const asOwner = await read(owner.authorization);
  expect(asOwner.includesPrivateActivity).toBe(true);
  expect(commits(await read(intruder.authorization))).toBe(3);
  expect(commits(await read())).toBe(3);
  expect(asOwner.totalContributions).toBeGreaterThanOrEqual(5);

  // File comments count as contributions, but not on private repositories for other viewers.
  const commentOn = (repo: string) =>
    bareApi.post(`/api/repos/${owner.username}/${repo}/file-comments`, { headers: { Authorization: owner.authorization }, data: { branch: 'main', path: 'heat/secret.txt'.replace('secret', repo === publicRepo.name ? 'one' : 'secret'), body: 'note' } });
  expect((await commentOn(publicRepo.name)).status()).toBe(201);
  expect((await commentOn(privateRepo.name)).status()).toBe(201);
  const comments = (c: Contributions) => c.days.reduce((sum, day) => sum + day.breakdown.fileComments, 0);
  expect(comments(await read(owner.authorization))).toBe(2);
  expect(comments(await read())).toBe(1);
});
