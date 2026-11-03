import { expect, test } from './fixtures/scenario';

test('discussions support categories, paged comments, replies, answers and moderation', async ({
  bareApi,
  owner,
  intruder,
  publicRepo,
}) => {
  const headers = { Authorization: owner.authorization };
  const other = { Authorization: intruder.authorization };
  const base = `/api/repos/${owner.username}/${publicRepo.name}/discussions`;

  // Maintainers only for announcements.
  expect(
    (
      await bareApi.post(base, {
        headers: other,
        data: { title: 'News', body: '', category: 'ANNOUNCEMENTS' },
      })
    ).status(),
  ).toBe(403);

  const created = await bareApi.post(base, {
    headers: other,
    data: { title: 'How do I deploy?', body: 'Any docs?', category: 'Q_AND_A' },
  });
  expect(created.status()).toBe(201);
  const number = (await created.json()).info.number;
  const path = `${base}/${number}`;

  await bareApi.post(base, {
    headers,
    data: { title: 'Welcome', body: 'Hello', category: 'ANNOUNCEMENTS' },
  });

  const questions = await (await bareApi.get(`${base}?category=Q_AND_A&unanswered=true`)).json();
  expect(questions.content.map((d: { title: string }) => d.title)).toEqual(['How do I deploy?']);
  expect((await (await bareApi.get(base)).json()).totalElements).toBe(2);

  // Comments come in pages; replies are fetched per comment.
  let firstId = '';
  for (let i = 0; i < 22; i++) {
    const response = await bareApi.post(`${path}/comments`, {
      headers,
      data: { body: `comment ${i}` },
    });
    expect(response.status()).toBe(201);
    if (i === 0) firstId = (await response.json()).id;
  }
  const pageOne = await (await bareApi.get(`${path}/comments`)).json();
  expect(pageOne).toMatchObject({ size: 20, totalElements: 22, totalPages: 2 });
  expect((await (await bareApi.get(`${path}/comments?page=1`)).json()).content).toHaveLength(2);

  const reply = await bareApi.post(`${path}/comments`, {
    headers: other,
    data: { body: 'a reply', parentId: firstId },
  });
  expect(reply.status()).toBe(201);
  const replyId = (await reply.json()).id;
  expect(
    (
      await bareApi.post(`${path}/comments`, {
        headers,
        data: { body: 'too deep', parentId: replyId },
      })
    ).status(),
  ).toBe(400);
  const replies = await (await bareApi.get(`${path}/comments/${firstId}/replies`)).json();
  expect(replies.content.map((c: { body: string }) => c.body)).toEqual(['a reply']);
  expect((await (await bareApi.get(`${path}/comments`)).json()).content[0].replyCount).toBe(1);

  // Only the asker or a maintainer picks the answer; it then shows on the discussion.
  const answerId = pageOne.content[1].id;
  expect(
    (
      await bareApi.put(`${path}/answer`, { headers: other, data: { commentId: answerId } })
    ).status(),
  ).toBe(200);
  const detail = await (await bareApi.get(path)).json();
  expect(detail.answer.id).toBe(answerId);
  expect(detail.info.answered).toBe(true);
  expect(
    (await (await bareApi.get(`${base}?category=Q_AND_A&unanswered=true`)).json()).content,
  ).toEqual([]);

  // Upvotes are idempotent per user.
  expect((await bareApi.put(`${path}/upvote`, { headers: other })).status()).toBe(200);
  const voted = await (await bareApi.put(`${path}/upvote`, { headers: other })).json();
  expect(voted.upvotes).toBe(1);
  expect((await (await bareApi.delete(`${path}/upvote`, { headers: other })).json()).upvotes).toBe(
    0,
  );

  // Locking stops everyone but maintainers from commenting.
  expect(
    (await bareApi.put(`${path}/lock`, { headers: other, data: { locked: true } })).status(),
  ).toBe(403);
  expect((await bareApi.put(`${path}/lock`, { headers, data: { locked: true } })).status()).toBe(
    200,
  );
  expect(
    (await bareApi.post(`${path}/comments`, { headers: other, data: { body: 'late' } })).status(),
  ).toBe(400);
  expect(
    (await bareApi.post(`${path}/comments`, { headers, data: { body: 'maintainer' } })).status(),
  ).toBe(201);

  expect((await bareApi.delete(path, { headers: other })).status()).toBe(204);
  expect((await bareApi.get(path)).status()).toBe(404);
});
