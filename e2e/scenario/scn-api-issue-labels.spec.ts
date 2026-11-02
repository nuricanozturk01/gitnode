import { expect, test } from './fixtures/scenario';
import { createScenarioIssue } from './helpers/issue-api';

test('repository labels enforce access and filter issues', async ({ bareApi, owner, intruder, publicRepo, privateRepo }) => {
  const headers = { Authorization: owner.authorization };
  const intruderHeaders = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  const privateBase = `/api/repos/${owner.username}/${privateRepo.name}`;
  const form = { name: 'Bug', color: 'FF0000', description: 'Defect' };

  expect((await bareApi.post(`${base}/labels`, { headers: intruderHeaders, data: form })).status()).toBe(403);
  expect((await bareApi.get(`${privateBase}/labels`, { headers: intruderHeaders })).status()).toBe(403);
  const created = await bareApi.post(`${base}/labels`, { headers, data: form });
  expect(created.status()).toBe(201);
  const label = await created.json() as { id: string; color: string };
  expect(label.color).toBe('ff0000');
  expect((await bareApi.post(`${base}/labels`, { headers, data: { ...form, name: 'bug' } })).status()).toBe(400);

  const first = await createScenarioIssue(bareApi, owner, owner.username, publicRepo.name, 'Labelled');
  const second = await createScenarioIssue(bareApi, owner, owner.username, publicRepo.name, 'Unlabelled');
  const issuePath = `${base}/issues/${first.number}`;
  expect((await bareApi.patch(issuePath, { headers: intruderHeaders, data: { addLabelId: label.id } })).status()).toBe(403);
  const assignments = await Promise.all(Array.from({ length: 8 }, () =>
    bareApi.patch(issuePath, { headers, data: { addLabelId: label.id } })));
  expect(assignments.map((response) => response.status())).toEqual(Array(8).fill(200));
  expect((await assignments[0].json()).labels.map((entry: { id: string }) => entry.id)).toContain(label.id);
  const filtered = await bareApi.get(`${base}/issues`, { params: { labelId: label.id } });
  expect(filtered.status()).toBe(200);
  const numbers = (await filtered.json()).content.map((issue: { number: number }) => issue.number);
  expect(numbers).toContain(first.number);
  expect(numbers).not.toContain(second.number);

  expect((await bareApi.put(`${base}/labels/${label.id}`, {
    headers, data: { ...form, name: 'Defect' },
  })).status()).toBe(200);
  expect((await bareApi.delete(`${base}/labels/${label.id}`, { headers })).status()).toBe(204);
  const reread = await bareApi.get(issuePath);
  expect((await reread.json()).labels).toEqual([]);
  const afterDelete = await bareApi.get(`${base}/issues`, { params: { labelId: label.id } });
  expect((await afterDelete.json()).content).toEqual([]);
});
