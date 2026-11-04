/**
 * Team management — per-owner teams (VIEWER, DEVELOPER, MAINTAINER, ADMIN) on the owner's repositories.
 */
import fs from 'node:fs';
import path from 'node:path';

import type { APIRequestContext } from '@playwright/test';

import { expect, test } from './fixtures/scenario';
import { deleteSelfUser } from './helpers/admin-api';
import {
  gitClone,
  gitCommitAll,
  gitPush,
  httpGitRemoteUrl,
  removeWorkDir,
  runGit,
  scenarioWorkDir,
} from './helpers/git';
import { createPullRequest, createScenarioRepo, getRepo } from './helpers/scenario-api';
import type { ScenarioUser } from './helpers/types';

interface TeamUser {
  id: string;
  username: string;
}

interface TeamMember {
  id: string;
  owner: TeamUser;
  member: TeamUser;
  role: string;
  status: string;
}

interface TeamPage {
  content: TeamMember[];
  size: number;
}

async function registerUser(api: APIRequestContext, prefix: string): Promise<ScenarioUser> {
  const username = `${prefix}_${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
  const password = 'Passw0rd!x1';
  const email = `${username}@example.org`;
  const response = await api.post('/api/auth/register', { data: { username, email, password } });
  expect(response.status()).toBe(200);
  const body = (await response.json()) as { token: string; refreshToken: string };
  return {
    username,
    email,
    password,
    token: body.token,
    refreshToken: body.refreshToken,
    expiresIn: 0,
    refreshExpiresIn: 0,
    authorization: `Bearer ${body.token}`,
  };
}

test.describe('SCN-TEAM — per-owner teams', () => {
  test('invitation lifecycle and role enforcement', async ({ bareApi }, testInfo) => {
    const owner = await registerUser(bareApi, 'teamowner');
    const member = await registerUser(bareApi, 'teammember');
    const other = await registerUser(bareApi, 'teamother');
    const users = [owner, member, other];
    const privateRepo = await createScenarioRepo(bareApi, owner, {
      isPrivate: true,
      namePrefix: 'team',
    });
    const otherRepo = await createScenarioRepo(bareApi, other, {
      isPrivate: true,
      namePrefix: 'team-other',
    });

    const repoApi = `/api/repos/${owner.username}/${privateRepo.name}`;
    const repoInfoUrl = `/api/repo/${owner.username}/${privateRepo.name}`;
    const workDir = scenarioWorkDir(`${testInfo.testId}-team`);
    const remote = httpGitRemoteUrl(owner.username, privateRepo.name, {
      username: member.username,
      password: member.password,
    });
    const branch = 'team-dev-branch';
    let membershipId: string | undefined;
    let ownerDeleted = false;

    const auth = (user: ScenarioUser) => ({ Authorization: user.authorization });
    const invite = (invitee: string, role: string, by: ScenarioUser = owner) =>
      bareApi.post('/api/team/invitations', {
        headers: auth(by),
        data: { username: invitee, role },
      });
    const listPage = async (url: string, user: ScenarioUser): Promise<TeamPage> => {
      const res = await bareApi.get(url, { headers: auth(user) });
      expect(res.status()).toBe(200);
      return (await res.json()) as TeamPage;
    };
    const pendingInvitationId = async (): Promise<string> => {
      const found = (await listPage('/api/team/invitations/mine', member)).content[0];
      expect(found).toBeDefined();
      expect(found.owner.username).toBe(owner.username);
      return found.id;
    };
    const accept = async () => {
      const res = await bareApi.post(
        `/api/team/invitations/${await pendingInvitationId()}/accept`,
        {
          headers: auth(member),
        },
      );
      expect(res.status()).toBe(200);
      return (await res.json()) as TeamMember;
    };
    const setRole = (role: string) =>
      bareApi.patch(`/api/team/members/${membershipId}/role`, {
        headers: auth(owner),
        data: { role },
      });
    const hasInviteNotification = async (invitationId: string): Promise<boolean> => {
      const res = await bareApi.get('/api/notifications?size=50', { headers: auth(member) });
      expect(res.status()).toBe(200);
      const page = (await res.json()) as {
        content: { type: string; entityId: string | null }[];
      };
      return page.content.some((n) => n.type === 'TEAM_INVITED' && n.entityId === invitationId);
    };
    const readRepo = (url = repoInfoUrl) => bareApi.get(url, { headers: auth(member) });

    try {
      await test.step('PENDING invitation grants nothing', async () => {
        const res = await invite(member.username, 'VIEWER');
        expect(res.status()).toBe(201);
        const created = (await res.json()) as TeamMember;
        expect(created.status).toBe('PENDING');
        expect(created.member.username).toBe(member.username);
        expect((await readRepo()).status()).toBe(403);
        const me = await bareApi.get('/api/team/me', { headers: auth(member) });
        expect(((await me.json()) as { pendingInvitations: number }).pendingInvitations).toBe(1);
      });

      await test.step('accepted VIEWER reads but cannot push', async () => {
        const accepted = await accept();
        membershipId = accepted.id;
        expect(accepted.role).toBe('VIEWER');
        expect((await readRepo()).status()).toBe(200);
        const teams = await listPage('/api/team/memberships', member);
        expect(teams.content.map((m) => m.owner.username)).toContain(owner.username);

        gitClone(remote, workDir);
        runGit(['-C', workDir, 'checkout', '-b', branch]);
        fs.writeFileSync(path.join(workDir, 'viewer.txt'), 'viewer\n', 'utf8');
        gitCommitAll(workDir, 'scenario: viewer push');
        const pushOut = gitPush(workDir, branch, true);
        expect(pushOut).toMatch(/403|denied|not allowed|unable to access|fatal/i);
      });

      await test.step('DEVELOPER pushes a branch and opens a PR but cannot merge', async () => {
        expect((await setRole('DEVELOPER')).status()).toBe(200);
        gitPush(workDir, branch);
        const pr = await createPullRequest(
          bareApi,
          member.authorization,
          owner.username,
          privateRepo.name,
          { title: 'team developer PR', sourceBranch: branch },
        );
        const reviewers = await bareApi.put(`${repoApi}/pulls/${pr.number}/reviewers`, {
          headers: auth(owner),
          data: { usernames: [owner.username] },
        });
        expect(reviewers.ok()).toBe(true);
        const mergeBody = { strategy: 'MERGE_COMMIT', commitMessage: `Merge PR #${pr.number}` };
        const denied = await bareApi.post(`${repoApi}/pulls/${pr.number}/merge`, {
          headers: auth(member),
          data: mergeBody,
        });
        expect(denied.status()).toBe(403);

        expect((await setRole('MAINTAINER')).status()).toBe(200);
        const merged = await bareApi.post(`${repoApi}/pulls/${pr.number}/merge`, {
          headers: auth(member),
          data: mergeBody,
        });
        expect(merged.status()).toBe(200);
      });

      await test.step("membership does not reach another user's repository", async () => {
        const res = await readRepo(`/api/repo/${other.username}/${otherRepo.name}`);
        expect(res.status()).toBe(403);
      });

      await test.step('removing the member revokes access on the next request', async () => {
        const res = await bareApi.delete(`/api/team/members/${membershipId}`, {
          headers: auth(owner),
        });
        expect(res.status()).toBe(204);
        membershipId = undefined;
        expect((await readRepo()).status()).toBe(403);
      });

      await test.step("nobody manages someone else's team", async () => {
        expect((await invite(member.username, 'DEVELOPER')).status()).toBe(201);
        const inviteId = await pendingInvitationId();
        const foreign = await listPage('/api/team/members?status=PENDING&size=50', other);
        expect(foreign.content).toHaveLength(0);
        expect(
          (
            await bareApi.delete(`/api/team/invitations/${inviteId}`, { headers: auth(other) })
          ).status(),
        ).toBe(404);
        expect(
          (
            await bareApi.patch(`/api/team/members/${inviteId}/role`, {
              headers: auth(other),
              data: { role: 'ADMIN' },
            })
          ).status(),
        ).toBe(404);
        expect(
          (
            await bareApi.delete(`/api/team/members/${inviteId}`, { headers: auth(other) })
          ).status(),
        ).toBe(404);
        expect(
          (
            await bareApi.post(`/api/team/invitations/${inviteId}/accept`, {
              headers: auth(other),
            })
          ).status(),
        ).toBe(404);
      });

      await test.step('self-invite is 400 and duplicate invite is 409', async () => {
        expect((await invite(owner.username, 'VIEWER')).status()).toBe(400);
        expect((await invite(member.username, 'VIEWER')).status()).toBe(409);
      });

      await test.step('declined invitation can be re-issued', async () => {
        const declinedId = await pendingInvitationId();
        await expect.poll(() => hasInviteNotification(declinedId)).toBe(true);
        const decline = await bareApi.post(`/api/team/invitations/${declinedId}/decline`, {
          headers: auth(member),
        });
        expect(decline.status()).toBe(204);
        await expect.poll(() => hasInviteNotification(declinedId)).toBe(false);
        const pending = await listPage('/api/team/members?status=PENDING&size=50', owner);
        expect(pending.content.find((m) => m.member.username === member.username)).toBeUndefined();

        const again = await invite(member.username, 'MAINTAINER');
        expect(again.status()).toBe(201);
        membershipId = ((await again.json()) as TeamMember).id;
        const accepted = await accept();
        expect(accepted.role).toBe('MAINTAINER');
        expect((await readRepo()).status()).toBe(200);
      });

      await test.step('member reads and forks the private repo, a stranger cannot', async () => {
        const repoId = (
          await getRepo(bareApi, owner.authorization, owner.username, privateRepo.name)
        ).id;
        const snippetsUrl = `/api/snippets/repo/${owner.username}/${privateRepo.name}`;
        const projectsUrl = `/api/projects/${owner.username}/by-repo/${repoId}`;
        const forkUrl = `/api/repo/${owner.username}/${privateRepo.name}/fork`;
        for (const url of [snippetsUrl, projectsUrl]) {
          expect((await bareApi.get(url, { headers: auth(member) })).status()).toBe(200);
          expect((await bareApi.get(url, { headers: auth(other) })).status()).toBe(403);
          expect((await bareApi.get(url)).status()).toBe(403);
        }
        expect((await bareApi.post(forkUrl, { headers: auth(other) })).status()).toBe(403);
        expect((await bareApi.post(forkUrl, { headers: auth(member) })).status()).toBe(200);
      });

      await test.step('member can leave and loses access', async () => {
        const leave = await bareApi.delete(`/api/team/memberships/${membershipId}`, {
          headers: auth(member),
        });
        expect(leave.status()).toBe(204);
        membershipId = undefined;
        expect((await readRepo()).status()).toBe(403);
      });

      await test.step('cancelled invitation removes the invitee notification', async () => {
        expect((await invite(member.username, 'VIEWER')).status()).toBe(201);
        const cancelledId = await pendingInvitationId();
        await expect.poll(() => hasInviteNotification(cancelledId)).toBe(true);
        const cancel = await bareApi.delete(`/api/team/invitations/${cancelledId}`, {
          headers: auth(owner),
        });
        expect(cancel.status()).toBe(204);
        await expect.poll(() => hasInviteNotification(cancelledId)).toBe(false);
      });

      await test.step('page size is capped at 50', async () => {
        const body = await listPage('/api/team/members?status=ACCEPTED&size=100000', owner);
        expect(body.content.length).toBeLessThanOrEqual(50);
        expect(body.size).toBeLessThanOrEqual(50);
      });

      await test.step('deleting the owner removes the pending invitation notification', async () => {
        expect((await invite(member.username, 'VIEWER')).status()).toBe(201);
        const pendingId = await pendingInvitationId();
        await expect.poll(() => hasInviteNotification(pendingId)).toBe(true);
        await deleteSelfUser(bareApi, owner.authorization);
        ownerDeleted = true;
        await expect.poll(() => hasInviteNotification(pendingId)).toBe(false);
      });
    } finally {
      for (const user of users) {
        if (user === owner && ownerDeleted) {
          continue;
        }
        await deleteSelfUser(bareApi, user.authorization);
      }
      removeWorkDir(workDir);
    }
  });
});
