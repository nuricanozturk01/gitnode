import { expect, test } from './fixtures/scenario';
import { createScenarioIssue } from './helpers/issue-api';

test('milestones enforce repository boundaries and filter issue assignments', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
  privateRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const intruderHeaders = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  const privateBase = `/api/repos/${owner.username}/${privateRepo.name}`;
  const form = {
    title: 'Release 1',
    description: 'First release',
    dueDate: '2027-01-01',
    status: 'OPEN',
  };
  expect(
    (await bareApi.post(`${base}/milestones`, { headers: intruderHeaders, data: form })).status(),
  ).toBe(403);
  expect(
    (await bareApi.get(`${privateBase}/milestones`, { headers: intruderHeaders })).status(),
  ).toBe(403);
  const created = await bareApi.post(`${base}/milestones`, { headers, data: form });
  expect(created.status()).toBe(201);
  const milestone = (await created.json()) as { id: string };
  expect(
    (
      await bareApi.post(`${base}/milestones`, { headers, data: { ...form, title: 'release 1' } })
    ).status(),
  ).toBe(400);
  const other = await bareApi.post(`${privateBase}/milestones`, { headers, data: form });
  const otherMilestone = (await other.json()) as { id: string };
  const first = await createScenarioIssue(
    bareApi,
    owner,
    owner.username,
    publicRepo.name,
    'Milestone issue',
  );
  const second = await createScenarioIssue(
    bareApi,
    owner,
    owner.username,
    publicRepo.name,
    'Other issue',
  );
  const issuePath = `${base}/issues/${first.number}`;
  expect(
    (
      await bareApi.patch(issuePath, { headers, data: { milestoneId: otherMilestone.id } })
    ).status(),
  ).toBe(404);
  expect(
    (
      await bareApi.patch(issuePath, {
        headers: intruderHeaders,
        data: { milestoneId: milestone.id },
      })
    ).status(),
  ).toBe(403);
  const assigned = await bareApi.patch(issuePath, { headers, data: { milestoneId: milestone.id } });
  expect(assigned.status()).toBe(200);
  expect((await assigned.json()).milestone.id).toBe(milestone.id);
  const filtered = await bareApi.get(`${base}/issues`, { params: { milestoneId: milestone.id } });
  expect(filtered.status()).toBe(200);
  const numbers = (await filtered.json()).content.map((issue: { number: number }) => issue.number);
  expect(numbers).toContain(first.number);
  expect(numbers).not.toContain(second.number);
  const labelResponse = await bareApi.post(`${base}/labels`, {
    headers,
    data: { name: 'tracked', color: '0969da' },
  });
  expect(labelResponse.status()).toBe(201);
  const label = (await labelResponse.json()) as { id: string };
  expect(
    (await bareApi.patch(issuePath, { headers, data: { addLabelId: label.id } })).status(),
  ).toBe(200);
  const combined = await bareApi.get(`${base}/issues`, {
    params: { milestoneId: milestone.id, labelId: label.id },
  });
  expect(combined.status()).toBe(200);
  expect((await combined.json()).content.map((issue: { number: number }) => issue.number)).toEqual([
    first.number,
  ]);
  const cleared = await bareApi.patch(issuePath, { headers, data: { clearMilestone: true } });
  expect((await cleared.json()).milestone).toBeNull();
  expect(
    (await bareApi.patch(issuePath, { headers, data: { milestoneId: milestone.id } })).status(),
  ).toBe(200);
  expect(
    (
      await bareApi.put(`${base}/milestones/${milestone.id}`, {
        headers,
        data: { ...form, status: 'CLOSED' },
      })
    ).status(),
  ).toBe(200);
  expect((await bareApi.delete(`${base}/milestones/${milestone.id}`, { headers })).status()).toBe(
    204,
  );
  expect((await (await bareApi.get(issuePath)).json()).milestone).toBeNull();
});
