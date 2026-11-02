import { expect, test } from './fixtures/scenario';
import { prepareFeatureBranch } from './helpers/scenario-api';

test('concurrent pull requests receive unique numbers', async ({ bareApi, owner, publicRepo }) => {
  const branches = Array.from({ length: 6 }, (_, index) => `feature-${index}`);
  for (const branch of branches) {
    await prepareFeatureBranch(bareApi, owner.authorization, owner.username, publicRepo.name, branch);
  }
  const path = `/api/repos/${owner.username}/${publicRepo.name}/pulls`;
  const responses = await Promise.all(branches.map((sourceBranch) => bareApi.post(path, {
    headers: { Authorization: owner.authorization },
    data: { title: sourceBranch, sourceBranch, targetBranch: 'main', isDraft: false },
  })));
  expect(responses.map((response) => response.status())).toEqual(Array(branches.length).fill(201));
  const pullRequests = await Promise.all(responses.map((response) => response.json() as Promise<{ number: number }>));
  expect(new Set(pullRequests.map((pullRequest) => pullRequest.number)).size).toBe(branches.length);
  await prepareFeatureBranch(bareApi, owner.authorization, owner.username, publicRepo.name, 'same-branch');
  const duplicates = await Promise.all(Array.from({ length: 4 }, () => bareApi.post(path, {
    headers: { Authorization: owner.authorization },
    data: { title: 'Same branch', sourceBranch: 'same-branch', targetBranch: 'main', isDraft: false },
  })));
  expect(duplicates.map((response) => response.status()).sort()).toEqual([201, 400, 400, 400]);
});
