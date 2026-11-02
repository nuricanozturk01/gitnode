import { expect, test } from './fixtures/scenario';
import { createPullRequest, prepareFeatureBranch } from './helpers/scenario-api';

test('line conversations can be resolved and reopened by the right people', async ({ bareApi, owner, intruder, publicRepo }) => {
  const headers = { Authorization: owner.authorization };
  const guest = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}`;
  await prepareFeatureBranch(bareApi, owner.authorization, owner.username, publicRepo.name, 'threads');
  const pr = await createPullRequest(bareApi, owner.authorization, owner.username, publicRepo.name, { title: 'Threads', sourceBranch: 'threads' });
  const comments = `${base}/pulls/${pr.number}/comments`;

  const line = await bareApi.post(comments, { headers, data: { body: 'Please rename', filePath: 'scenario/threads.txt', lineNumber: 1, lineSide: 'RIGHT' } });
  expect(line.status()).toBe(201);
  const lineComment = await line.json();
  const general = await (await bareApi.post(comments, { headers, data: { body: 'General' } })).json();
  expect(lineComment.isResolved).toBe(false);

  const resolve = (id: string, resolved: boolean, auth: Record<string, string>) => bareApi.put(`${comments}/${id}/resolved`, { headers: auth, data: { resolved } });
  expect((await resolve(general.id, true, headers)).status()).toBe(400);
  expect((await bareApi.put(`${comments}/${lineComment.id}/resolved`, { data: { resolved: true } })).status()).toBe(401);

  const byAuthor = await resolve(lineComment.id, true, headers);
  expect(byAuthor.status()).toBe(200);
  expect((await byAuthor.json()).isResolved).toBe(true);
  const reopened = await resolve(lineComment.id, false, headers);
  expect((await reopened.json()).isResolved).toBe(false);
  // Someone with read access who did not write the comment and is not the PR author is refused.
  expect((await resolve(lineComment.id, true, guest)).status()).toBe(403);

  const listed = await (await bareApi.get(comments, { headers })).json();
  expect(listed.content.find((c: { id: string }) => c.id === lineComment.id).isResolved).toBe(false);

  // A third party with no relation to the pull request cannot resolve it.
  const stranger = await bareApi.post('/api/auth/register', { data: { username: `st_${Date.now().toString(36)}`, email: `st_${Date.now().toString(36)}@example.org`, password: 'Passw0rd!x1' } });
  const strangerAuth = { Authorization: `Bearer ${(await stranger.json()).token}` };
  expect((await resolve(lineComment.id, true, strangerAuth)).status()).toBe(403);
});
