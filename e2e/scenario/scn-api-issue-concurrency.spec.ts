import { expect, test } from './fixtures/scenario';

test('concurrent issue creation assigns a unique number to each issue', async ({ bareApi, owner, publicRepo }) => {
  const path = `/api/repos/${owner.username}/${publicRepo.name}/issues`;
  const responses = await Promise.all(Array.from({ length: 8 }, (_, index) => bareApi.post(path, {
    headers: { Authorization: owner.authorization },
    data: { title: `Concurrent issue ${index}`, description: 'Concurrency test' },
  })));
  expect(responses.map((response) => response.status())).toEqual(Array(8).fill(201));
  const issues = await Promise.all(responses.map((response) => response.json() as Promise<{ number: number }>));
  expect(new Set(issues.map((issue) => issue.number)).size).toBe(8);
});
