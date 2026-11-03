import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { getApiBaseUrl } from '@helpers/env';
import { expect, request as playwrightRequest, test } from '@playwright/test';

import { createPullRequest, createScenarioRepo, prepareFeatureBranch, putBlob } from './helpers/scenario-api';
import { bootstrapScenarioProject, createBranchFromTask, createScenarioSubtask, createScenarioTask, getProjectLinkedRepos } from './helpers/task-api';
import type { ScenarioUser } from './helpers/types';

// Modules reference accounts and repositories by id; the database owns deletes (ON DELETE CASCADE, and SET NULL
// for issues.assignee_id). One guest account takes part in every feature and is deleted once at the end, which
// must remove exactly the rows it owns everywhere while the owner's data stays readable. Uses 3 own accounts
// (registration is rate limited to 20 per 5 minutes) and leaves the shared session users untouched.
test('account lifecycle across file comments, ssh keys, releases, snippets and issues', async () => {
  test.setTimeout(120_000);
  const ctx = await playwrightRequest.newContext({ baseURL: getApiBaseUrl() });
  const dir = mkdtempSync(join(tmpdir(), 'gitnode-lifecycle-'));
  const password = 'Passw0rd!x1';
  const suffix = `${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
  const register = async (prefix: string): Promise<ScenarioUser> => {
    const username = `${prefix}_${suffix}`;
    const response = await ctx.post('/api/auth/register', { data: { username, email: `${username}@example.org`, password } });
    expect(response.status()).toBe(200);
    const body = (await response.json()) as { token: string; refreshToken: string };
    return { username, email: `${username}@example.org`, password, token: body.token, refreshToken: body.refreshToken, expiresIn: 0, refreshExpiresIn: 0, authorization: `Bearer ${body.token}` };
  };
  const owner = await register('lcowner');
  const viewer = await register('lcview');
  const guest = await register('lcguest');
  const o = { Authorization: owner.authorization };
  const v = { Authorization: viewer.authorization };
  const g = { Authorization: guest.authorization };
  const names = (list: { username: string }[]) => list.map((u) => u.username).sort();
  const sshEnv = (name: string) => {
    const key = join(dir, name);
    execFileSync('ssh-keygen', ['-q', '-t', 'ed25519', '-N', '', '-f', key]);
    return { key, env: { GIT_SSH_COMMAND: `ssh -i ${key} -p 12222 -o IdentitiesOnly=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null` } };
  };
  const git = (env: Record<string, string>, ...args: string[]) => execFileSync('git', args, { env: { ...process.env, ...env }, stdio: 'pipe' });
  const remote = (repo: string) => `ssh://git@127.0.0.1:12222/${owner.username}/${repo}.git`;
  const fails = (fn: () => unknown) => {
    try {
      fn();
    } catch (error) {
      return String((error as { stderr?: Buffer }).stderr ?? error);
    }
    throw new Error('expected the git command to fail');
  };
  try {
    const pub = await createScenarioRepo(ctx, owner, { isPrivate: false, namePrefix: 'lcpub' });
    const priv = await createScenarioRepo(ctx, owner, { isPrivate: true, namePrefix: 'lcpriv' });
    const base = `/api/repos/${owner.username}/${pub.name}`;
    const head = () => ctx.get(`${base}/commits`, { headers: o, params: { branch: 'main', page: '0', size: '1' } }).then((r) => r.json()).then((b) => b.items[0].sha as string);

    await test.step('file comments: owner thread, guest thread and reply', async () => {
      await putBlob(ctx, owner.authorization, owner.username, pub.name, 'main', 'cascade.md', 'a\nb\n', 'Add cascade file');
      const fc = `${base}/file-comments`;
      const root = await ctx.post(fc, { headers: o, data: { branch: 'main', path: 'cascade.md', line: 1, body: 'owner thread' } });
      expect(root.status()).toBe(201);
      const rootId = (await root.json()).id as string;
      expect((await ctx.post(fc, { headers: g, data: { branch: 'main', path: 'cascade.md', line: 2, body: 'guest thread' } })).status()).toBe(201);
      expect((await ctx.post(fc, { headers: g, data: { branch: 'main', path: 'cascade.md', body: 'guest reply', parentId: rootId } })).status()).toBe(201);
      const list = await (await ctx.get(`${fc}?branch=main&path=cascade.md`, { headers: o })).json();
      expect(list.comments.map((c: { author: { username: string } }) => c.author.username).sort()).toEqual([owner.username, guest.username, guest.username].sort());
    });

    await test.step('ssh: owner reads/writes private, guest is denied', async () => {
      const ownerKey = sshEnv('owner');
      const guestKey = sshEnv('guest');
      for (const [user, k, title] of [[o, ownerKey, 'owner key'], [g, guestKey, 'guest key']] as const) {
        const response = await ctx.post('/api/user/ssh-keys', { headers: user, data: { title, publicKey: readFileSync(`${k.key}.pub`, 'utf8') } });
        expect(response.status()).toBe(201);
      }
      git(ownerKey.env, 'clone', remote(priv.name), join(dir, 'owner-priv'));
      writeFileSync(join(dir, 'owner-priv', 'ssh.txt'), 'owner push\n');
      git(ownerKey.env, '-C', join(dir, 'owner-priv'), 'add', '.');
      git(ownerKey.env, '-C', join(dir, 'owner-priv'), '-c', 'user.name=a', '-c', 'user.email=a@example.org', 'commit', '-m', 'ssh push');
      git(ownerKey.env, '-C', join(dir, 'owner-priv'), 'push', 'origin', 'HEAD:main');
      expect(fails(() => git(guestKey.env, 'clone', remote(priv.name), join(dir, 'guest-priv')))).toMatch(/Could not read from remote repository|denied/i);
      git(guestKey.env, 'clone', remote(pub.name), join(dir, 'guest-pub'));
      writeFileSync(join(dir, 'guest-pub', 'x.txt'), 'nope\n');
      git(guestKey.env, '-C', join(dir, 'guest-pub'), 'add', '.');
      git(guestKey.env, '-C', join(dir, 'guest-pub'), '-c', 'user.name=b', '-c', 'user.email=b@example.org', 'commit', '-m', 'denied');
      expect(fails(() => git(guestKey.env, '-C', join(dir, 'guest-pub'), 'push', 'origin', 'HEAD:main'))).toMatch(/Could not read from remote repository|denied|rejected|fatal/i);
      (globalThis as { __guestEnv?: Record<string, string> }).__guestEnv = guestKey.env;
    });

    await test.step('releases: collaborator with push access creates one', async () => {
      expect((await ctx.post(`${base}/collaborators`, { headers: o, data: { username: guest.username, permissions: ['READ', 'PUSH'] } })).status()).toBe(201);
      expect((await ctx.post(`${base}/collaborators/invitation/accept`, { headers: g })).status()).toBe(200);
      const sha = await head();
      for (const tag of ['v1.0.0', 'v2.0.0']) {
        expect((await ctx.post(`${base}/tags`, { headers: o, data: { name: tag, sha, message: tag } })).status()).toBe(201);
      }
      const release = (headers: Record<string, string>, tagName: string) => ctx.post(`${base}/releases`, { headers, data: { tagName, name: `Release ${tagName}`, body: 'notes', isDraft: false, isPrerelease: false, createNewTag: false } });
      const first = await release(o, 'v1.0.0');
      expect(first.status()).toBe(201);
      expect((await first.json()).author.username).toBe(owner.username);
      const second = await release(g, 'v2.0.0');
      expect(second.status()).toBe(201);
      expect((await second.json()).author.username).toBe(guest.username);
      const all = (await (await ctx.get(`${base}/releases`, { headers: o })).json()) as { tagName: string; isLatest: boolean; author: { username: string } }[];
      expect(all.map((r) => `${r.tagName}:${r.author.username}`).sort()).toEqual([`v1.0.0:${owner.username}`, `v2.0.0:${guest.username}`]);
      expect(all.filter((r) => r.isLatest)).toHaveLength(1);
    });

    let snippetId = '';
    let forkId = '';
    await test.step('snippets: link, comment, revise, fork', async () => {
      const created = await ctx.post('/api/snippets', { headers: o, data: { title: 'Alpha', description: 'first', visibility: 'PUBLIC', files: [{ filename: 'a.txt', content: 'hello' }, { filename: 'b.txt', content: 'world' }] } });
      expect(created.status()).toBe(201);
      const snippet = await created.json();
      expect(snippet.owner).toMatchObject({ username: owner.username, email: owner.email });
      expect(snippet.files.map((f: { filename: string; content: string }) => `${f.filename}:${f.content}`)).toEqual(['a.txt:hello', 'b.txt:world']);
      snippetId = snippet.id as string;
      const linked = await ctx.put(`/api/snippets/${snippetId}/repo/${pub.id}`, { headers: o });
      expect((await linked.json()).repos).toEqual([{ id: pub.id, name: pub.name }]);
      const byRepo = await (await ctx.get(`/api/snippets/repo/${owner.username}/${pub.name}`, { headers: o })).json();
      expect(byRepo.content.map((s: { id: string }) => s.id)).toEqual([snippetId]);
      expect((await ctx.put(`/api/snippets/${snippetId}/repo/${pub.id}`, { headers: g })).status()).toBe(403);
      const comment = await ctx.post(`/api/snippets/${snippetId}/comments`, { headers: g, data: { body: 'nice' } });
      expect(comment.status()).toBe(201);
      const commentId = (await comment.json()).id as string;
      expect((await ctx.post(`/api/snippets/${snippetId}/comments`, { headers: v, data: { body: 'viewer comment' } })).status()).toBe(201);
      expect((await ctx.delete(`/api/snippets/${snippetId}/comments/${commentId}`, { headers: v })).status()).toBe(403);
      expect((await ctx.patch(`/api/snippets/${snippetId}`, { headers: o, data: { title: 'Alpha 2', summary: 'rename' } })).status()).toBe(200);
      const revisions = await (await ctx.get(`/api/snippets/${snippetId}/revisions`, { headers: o })).json();
      expect(revisions.content).toHaveLength(2);
      expect(revisions.content.every((r: { author: { username: string } }) => r.author.username === owner.username)).toBe(true);
      const fork = await ctx.post(`/api/snippets/${snippetId}/fork`, { headers: g });
      expect(fork.status()).toBe(201);
      const forkDetail = await fork.json();
      forkId = forkDetail.id as string;
      expect(forkDetail.owner.username).toBe(guest.username);
      expect(forkDetail.forkedFrom).toMatchObject({ id: snippetId, title: 'Alpha 2', owner: { username: owner.username } });
      const original = await (await ctx.get(`/api/snippets/${snippetId}`, { headers: o })).json();
      expect(original.forkCount).toBe(1);
      expect(original.commentCount).toBe(2);
      const mine = await (await ctx.get('/api/snippets/me', { headers: g })).json();
      expect(mine.content.map((s: { id: string }) => s.id)).toEqual([forkId]);
      const byOwner = await (await ctx.get(`/api/snippets/by-owner/${owner.username}`, { headers: v })).json();
      expect(byOwner.content.map((s: { id: string }) => s.id)).toEqual([snippetId]);
      const priv = await ctx.post('/api/snippets', { headers: o, data: { title: 'Secret', visibility: 'PRIVATE', files: [{ filename: 's.txt', content: 'x' }] } });
      const privId = (await priv.json()).id as string;
      expect((await ctx.get(`/api/snippets/${privId}`, { headers: v })).status()).toBe(404);
      expect(((await (await ctx.get(`/api/snippets/by-owner/${owner.username}`, { headers: v })).json()).content as { id: string }[]).map((s) => s.id)).not.toContain(privId);
      expect(((await (await ctx.get(`/api/snippets/by-owner/${owner.username}`, { headers: o })).json()).content as { id: string }[]).map((s) => s.id)).toContain(privId);
    });

    const issues = `${base}/issues`;
    await test.step('issues: authors, assignees, comments and the fallback primary assignee', async () => {
      const guestId = ((await (await ctx.get('/api/users/me', { headers: g })).json()) as { id: string }).id;
      const first = await ctx.post(issues, { headers: g, data: { title: 'Guest issue', description: 'by guest' } });
      expect(first.status()).toBe(201);
      expect((await first.json()).author).toMatchObject({ username: guest.username, email: guest.email });
      const second = await ctx.post(issues, { headers: o, data: { title: 'Owner issue', assigneeId: guestId } });
      expect(second.status()).toBe(201);
      const detail = await second.json();
      expect(detail.number).toBe(2);
      expect(detail.assignee.username).toBe(guest.username);
      expect(names(detail.assignees)).toEqual([guest.username]);
      const list = await (await ctx.get(issues, { headers: o, params: { status: 'OPEN', page: '0' } })).json();
      expect(list.content.map((i: { number: number }) => i.number).sort()).toEqual([1, 2]);
      expect((await ctx.patch(`${issues}/2`, { headers: o, data: { addAssigneeUsername: viewer.username } })).status()).toBe(200);
      const both = await (await ctx.get(`${issues}/2`, { headers: o })).json();
      expect(names(both.assignees)).toEqual([guest.username, viewer.username].sort());
      const removed = await (await ctx.patch(`${issues}/2`, { headers: o, data: { removeAssigneeUsername: guest.username } })).json();
      expect(names(removed.assignees)).toEqual([viewer.username]);
      expect(removed.assignee.username).toBe(viewer.username);
      const reset = await (await ctx.patch(`${issues}/2`, { headers: o, data: { assigneeUsername: guest.username } })).json();
      expect(reset.assignee.username).toBe(guest.username);
      expect(names(reset.assignees)).toEqual([guest.username]);
      expect((await ctx.post(`${issues}/2/comments`, { headers: g, data: { body: 'guest says' } })).status()).toBe(201);
      const viewerComment = await ctx.post(`${issues}/2/comments`, { headers: v, data: { body: 'viewer says' } });
      const comments = await (await ctx.get(`${issues}/2/comments`, { headers: o })).json();
      expect(comments.content.map((c: { author: { username: string }; body: string }) => `${c.author.username}:${c.body}`)).toEqual([`${guest.username}:guest says`, `${viewer.username}:viewer says`]);
      expect((await ctx.patch(`${issues}/2/comments/${(await viewerComment.json()).id}`, { headers: g, data: { body: 'hijack' } })).status()).toBe(403);
      expect((await (await ctx.get(`${issues}/2`, { headers: o })).json()).commentCount).toBe(2);
    });

    let projectCode = '';
    await test.step('projects and tasks: owner project with assignee and branch repo, guest-owned project', async () => {
      const project = await bootstrapScenarioProject(ctx, owner, pub);
      projectCode = project.projectCode;
      const guestId = ((await (await ctx.get('/api/users/me', { headers: g })).json()) as { id: string }).id;
      const tasks = `/api/projects/${owner.username}/${projectCode}/tasks`;
      const assigned = await ctx.post(tasks, { headers: o, data: { title: 'Assigned', description: 'x', boardColumnId: project.columnId, type: 'TASK', position: 0, assigneeId: guestId } });
      expect(assigned.status()).toBe(201);
      const assignedBody = await assigned.json();
      expect(assignedBody.assignee).toMatchObject({ username: guest.username });
      const branchTask = await createScenarioTask(ctx, owner, project, 'With branch');
      const subtask = await createScenarioSubtask(ctx, owner, projectCode, branchTask, 'Sub');
      const taskBranch = await createBranchFromTask(ctx, owner, owner.username, projectCode, branchTask, pub);
      expect(taskBranch).toBeTruthy();
      const detail = await (await ctx.get(`${tasks}/${branchTask}`, { headers: o })).json();
      expect(detail.branchRepoId).toBe(pub.id);
      expect(subtask).toBeTruthy();
      const linked = await getProjectLinkedRepos(ctx, owner, projectCode);
      expect(linked.map((r) => r.id ?? r.repoId)).toContain(pub.id);
      const assignedDetail = await (await ctx.get(`${tasks}/${assignedBody.code}`, { headers: o })).json();
      expect(assignedDetail.assignee.username).toBe(guest.username);
      const byOwner = await (await ctx.get(`/api/projects/${owner.username}/${projectCode}`, { headers: o })).json();
      expect(byOwner.owner?.username ?? owner.username).toBe(owner.username);
      const ownerProjects = await (await ctx.get(`/api/projects/${owner.username}`, { headers: o })).json();
      const ownerList = (Array.isArray(ownerProjects) ? ownerProjects : ownerProjects.content) as { codePrefix: string }[];
      expect(ownerList.map((p) => p.codePrefix)).toContain(projectCode);
      // the guest owns a project too; it must disappear with the account
      const guestProject = await ctx.post(`/api/projects/${guest.username}`, { headers: g, data: { name: 'Guest project', description: 'g', codePrefix: `G${suffix.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 5)}`, isPublic: true } });
      expect(guestProject.status()).toBe(201);
      const guestCode = (await guestProject.json()).codePrefix as string;
      expect((await ctx.get(`/api/projects/${guest.username}/${guestCode}`, { headers: v })).status()).toBe(200);
      (globalThis as { __guestProject?: string }).__guestProject = guestCode;
      (globalThis as { __assignedTask?: string }).__assignedTask = assignedBody.code as string;
      (globalThis as { __branchTask?: string }).__branchTask = branchTask;
    });

    const pulls = `${base}/pulls`;
    await test.step('pull requests: author, reviewer, review, comments, assignee and merger', async () => {
      expect((await ctx.put(`${base}/collaborators/${guest.username}/permissions`, { headers: o, data: { permissions: ['READ', 'PUSH', 'PULL_REQUEST_CREATE', 'PULL_REQUEST_REVIEW', 'PULL_REQUEST_MERGE'] } })).status()).toBe(200);
      await prepareFeatureBranch(ctx, owner.authorization, owner.username, pub.name, 'feat-a');
      await prepareFeatureBranch(ctx, owner.authorization, owner.username, pub.name, 'feat-b');
      const pr1 = await createPullRequest(ctx, guest.authorization, owner.username, pub.name, { title: 'Guest PR', sourceBranch: 'feat-a' });
      const pr2 = await createPullRequest(ctx, owner.authorization, owner.username, pub.name, { title: 'Owner PR', sourceBranch: 'feat-b' });
      expect(pr1.number).toBe(1);
      expect(pr2.number).toBe(2);
      const detail1 = await (await ctx.get(`${pulls}/1`, { headers: o })).json();
      expect(detail1.author).toMatchObject({ username: guest.username, email: guest.email });

      // reviewers, review, comments, assignees on the owner's PR
      const reviewers = await ctx.put(`${pulls}/2/reviewers`, { headers: o, data: { usernames: [guest.username] } });
      expect(reviewers.status()).toBe(200);
      expect(((await reviewers.json()) as { username: string }[]).map((r) => r.username)).toEqual([guest.username]);
      const shas = (await (await ctx.get(`${pulls}/2/reviews`, { headers: o })).json()) as { headSha: string; targetSha: string };
      const review = await ctx.post(`${pulls}/2/reviews`, { headers: g, data: { state: 'APPROVED', body: '', expectedSha: shas.headSha, expectedTargetSha: shas.targetSha } });
      expect(review.status()).toBe(201);
      expect((await review.json()).username).toBe(guest.username);
      const cg = await ctx.post(`${pulls}/2/comments`, { headers: g, data: { body: 'guest pr comment' } });
      expect(cg.status()).toBe(201);
      expect((await cg.json()).author.username).toBe(guest.username);
      expect((await ctx.post(`${pulls}/2/comments`, { headers: o, data: { body: 'owner pr comment' } })).status()).toBe(201);
      expect((await ctx.put(`${pulls}/2/metadata`, { headers: o, data: { labelIds: [], assignees: [guest.username, owner.username], milestoneId: null } })).status()).toBe(200);
      const meta = await (await ctx.get(`${pulls}/2/metadata`, { headers: o })).json();
      expect(names(meta.assignees)).toEqual([guest.username, owner.username].sort());

      // the guest merges the owner's PR (merged_by_id = guest)
      const merged = await ctx.post(`${pulls}/2/merge`, { headers: g, data: { strategy: 'MERGE_COMMIT', commitMessage: 'Merge PR #2' } });
      expect(merged.status()).toBe(200);
      const detail2 = await (await ctx.get(`${pulls}/2`, { headers: o })).json();
      expect(detail2.author.username).toBe(owner.username);
      expect(detail2.mergedBy.username).toBe(guest.username);
      const reviews = (await (await ctx.get(`${pulls}/2/reviews`, { headers: o })).json()) as { reviews: { username: string }[] };
      expect(reviews.reviews.map((r) => r.username)).toEqual([guest.username]);
      const prComments = await (await ctx.get(`${pulls}/2/comments`, { headers: o })).json();
      const list = (Array.isArray(prComments) ? prComments : prComments.content) as { author: { username: string } }[];
      expect(names(list.map((c) => c.author))).toEqual([guest.username, owner.username].sort());
    });

    await test.step('deleting the guest account removes exactly its rows everywhere', async () => {
      expect((await ctx.delete('/api/users/me', { headers: g })).status()).toBeLessThan(300);

      // file comments: only the owner's thread remains
      const fc = await (await ctx.get(`${base}/file-comments?branch=main&path=cascade.md`, { headers: o })).json();
      expect(fc.comments.map((c: { body: string }) => c.body)).toEqual(['owner thread']);

      // ssh: the guest's key is gone
      expect(fails(() => git((globalThis as { __guestEnv?: Record<string, string> }).__guestEnv ?? {}, 'clone', remote(pub.name), join(dir, 'guest-after')))).toMatch(/Permission denied|denied|authentication|Could not read/i);

      // releases: the guest's release disappeared with it
      const releases = await ctx.get(`${base}/releases`, { headers: o });
      expect(((await releases.json()) as { tagName: string }[]).map((r) => r.tagName)).toEqual(['v1.0.0']);
      expect((await ctx.get(`${base}/releases/tag/v2.0.0`, { headers: o })).status()).toBe(404);

      // snippets: fork and comment are gone, the viewer's comment and the original stay
      expect((await ctx.get(`/api/snippets/${forkId}`, { headers: o })).status()).toBe(404);
      const snippetComments = await (await ctx.get(`/api/snippets/${snippetId}/comments`, { headers: o })).json();
      expect(snippetComments.content.map((c: { author: { username: string } }) => c.author.username)).toEqual([viewer.username]);

      // issues: authored issue cascades, assignee is set to NULL, the guest's comment is gone
      expect((await ctx.get(`${issues}/1`, { headers: o })).status()).toBe(404);
      const issue2 = await (await ctx.get(`${issues}/2`, { headers: o })).json();
      expect(issue2.author.username).toBe(owner.username);
      expect(issue2.assignee).toBeNull();
      expect(issue2.assignees).toEqual([]);
      expect(issue2.commentCount).toBe(1);
      const issueComments = await (await ctx.get(`${issues}/2/comments`, { headers: o })).json();
      expect(issueComments.content.map((c: { author: { username: string } }) => c.author.username)).toEqual([viewer.username]);
      const open = await (await ctx.get(issues, { headers: o, params: { status: 'OPEN', page: '0' } })).json();
      expect(open.content.map((i: { number: number }) => i.number)).toEqual([2]);

      // pull requests: authored PR cascades, merged_by is set to NULL, reviewer/assignee/review/comment rows go
      expect((await ctx.get(`${pulls}/1`, { headers: o })).status()).toBe(404);
      const after2 = await (await ctx.get(`${pulls}/2`, { headers: o })).json();
      expect(after2.author.username).toBe(owner.username);
      expect(after2.mergedBy).toBeNull();
      expect(after2.status).toBe('MERGED');
      expect(((await (await ctx.get(`${pulls}/2/reviewers`, { headers: o })).json()) as unknown[]).length).toBe(0);
      const afterReviews = (await (await ctx.get(`${pulls}/2/reviews`, { headers: o })).json()) as { reviews: unknown[] };
      expect(afterReviews.reviews).toHaveLength(0);
      const afterMeta = await (await ctx.get(`${pulls}/2/metadata`, { headers: o })).json();
      expect(names(afterMeta.assignees)).toEqual([owner.username]);
      const afterComments = await (await ctx.get(`${pulls}/2/comments`, { headers: o })).json();
      const afterList = (Array.isArray(afterComments) ? afterComments : afterComments.content) as { author: { username: string } }[];
      expect(afterList.map((c) => c.author.username)).toEqual([owner.username]);

      // tasks: assignee SET NULL, the guest's project is gone with the account
      const g2 = globalThis as { __guestProject?: string; __assignedTask?: string };
      const taskAfter = await (await ctx.get(`/api/projects/${owner.username}/${projectCode}/tasks/${g2.__assignedTask}`, { headers: o })).json();
      expect(taskAfter.assignee).toBeNull();
      expect((await ctx.get(`/api/projects/${guest.username}/${g2.__guestProject}`, { headers: v })).status()).toBe(404);
    });

    await test.step('deleting the repository clears snippet links and its releases/issues', async () => {
      expect((await ctx.delete(`/api/repo/${owner.username}/${pub.name}`, { headers: o })).status()).toBeLessThan(300);
      const snippet = await ctx.get(`/api/snippets/${snippetId}`, { headers: o });
      expect(snippet.status()).toBe(200);
      expect((await snippet.json()).repos).toEqual([]);
      expect((await ctx.get(`/api/repo/${owner.username}/${pub.name}`, { headers: o })).status()).toBe(404);
      // projects and tasks survive the repository: the link row cascades, the task's branch repo is set to NULL
      const afterBranch = await (await ctx.get(`/api/projects/${owner.username}/${projectCode}/tasks/${(globalThis as { __branchTask?: string }).__branchTask}`, { headers: o })).json();
      expect(afterBranch.branchRepoId).toBeNull();
      expect(await getProjectLinkedRepos(ctx, owner, projectCode)).toEqual([]);
    });
  } finally {
    for (const user of [owner, viewer, guest]) {
      await ctx.delete('/api/users/me', { headers: { Authorization: user.authorization } });
    }
    await ctx.dispose();
    rmSync(dir, { recursive: true, force: true });
  }
});
