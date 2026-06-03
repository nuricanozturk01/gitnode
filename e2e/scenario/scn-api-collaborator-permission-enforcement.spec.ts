/**
 * SCN-API-COLLAB-PERM — Collaborator permission enforcement.
 *
 * Each section grants a collaborator a specific permission set, exercises the
 * protected endpoint, then verifies the correct allow/deny behaviour.
 *
 * Covers:
 *  PULL_REQUEST_CREATE — create PR (403 without, 201 with)
 *  PULL_REQUEST_REVIEW — add PR comment (403 without, 201 with)
 *  PULL_REQUEST_MERGE  — merge PR (403 without, 200 with)
 *  ISSUE_MANAGE        — update issue status (403 without, 200 with)
 *  SETTINGS_WRITE      — patch repo description (403 without, 200 with)
 *  ADMIN               — implies every permission (spot-check)
 */
import type { APIRequestContext } from '@playwright/test';

import { expect, test } from './fixtures/scenario';
import { deleteUser, registerAndLogin } from './helpers/register-and-login';
import { prepareFeatureBranch } from './helpers/scenario-api';

function repoCollaboratorsApi(owner: string, repo: string): string {
  return `/api/repos/${owner}/${repo}/collaborators`;
}

async function setPermissions(
  bareApi: APIRequestContext,
  ownerAuth: string,
  owner: string,
  repo: string,
  username: string,
  permissions: string[],
): Promise<void> {
  const resp = await bareApi.put(`${repoCollaboratorsApi(owner, repo)}/${username}/permissions`, {
    headers: { Authorization: ownerAuth },
    data: { permissions },
  });
  if (!resp.ok()) {
    throw new Error(`set perms failed (${resp.status()}): ${await resp.text()}`);
  }
}

// ─── SCN-COLLAB-PERM-PR-CREATE ────────────────────────────────────────────────

test.describe('SCN-COLLAB-PERM-PR-CREATE — PULL_REQUEST_CREATE enforcement', () => {
  test('collab without PR_CREATE cannot create PR; with PR_CREATE can create PR', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const username = `scn-prcreate-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, username);

    try {
      // Setup: feature branch owned by owner
      const featureBranch = `feat-perm-test-${Date.now().toString(36)}`;
      await prepareFeatureBranch(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        featureBranch,
      );

      // Invite with READ only
      const inviteResp = await bareApi.post(
        repoCollaboratorsApi(owner.username, privateRepo.name),
        {
          headers: { Authorization: owner.authorization },
          data: { username, permissions: ['READ'] },
        },
      );
      expect(inviteResp.status()).toBe(201);
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        {
          headers: { Authorization: collabAuth },
        },
      );

      // Without PR_CREATE → 403
      const denyResp = await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/pulls`,
        {
          headers: { Authorization: collabAuth },
          data: {
            title: 'Denied PR',
            description: '',
            sourceBranch: featureBranch,
            targetBranch: 'main',
            isDraft: false,
          },
        },
      );
      expect(denyResp.status()).toBe(403);

      // Grant PR_CREATE
      await setPermissions(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        username,
        ['READ', 'PULL_REQUEST_CREATE'],
      );

      // With PR_CREATE → 201
      const allowResp = await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/pulls`,
        {
          headers: { Authorization: collabAuth },
          data: {
            title: 'Allowed PR',
            description: '',
            sourceBranch: featureBranch,
            targetBranch: 'main',
            isDraft: false,
          },
        },
      );
      expect(allowResp.status()).toBe(201);
      const pr = await allowResp.json();
      expect(pr.status).toBe('OPEN');
    } finally {
      await deleteUser(bareApi, collabAuth);
    }
  });
});

// ─── SCN-COLLAB-PERM-PR-REVIEW ────────────────────────────────────────────────

test.describe('SCN-COLLAB-PERM-PR-REVIEW — PULL_REQUEST_REVIEW enforcement', () => {
  test('collab without PR_REVIEW cannot comment; with PR_REVIEW can comment', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const username = `scn-prreview-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, username);

    try {
      // Owner creates a PR for testing
      const featureBranch = `feat-review-test-${Date.now().toString(36)}`;
      await prepareFeatureBranch(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        featureBranch,
      );
      const prResp = await bareApi.post(`/api/repos/${owner.username}/${privateRepo.name}/pulls`, {
        headers: { Authorization: owner.authorization },
        data: {
          title: 'Review Test PR',
          description: '',
          sourceBranch: featureBranch,
          targetBranch: 'main',
          isDraft: false,
        },
      });
      expect(prResp.status()).toBe(201);
      const { number: prNumber } = await prResp.json();

      // Invite with READ only
      await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username, permissions: ['READ'] },
      });
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        {
          headers: { Authorization: collabAuth },
        },
      );

      // Without PR_REVIEW → 403
      const denyResp = await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/pulls/${prNumber}/comments`,
        {
          headers: { Authorization: collabAuth },
          data: { body: 'Denied comment' },
        },
      );
      expect(denyResp.status()).toBe(403);

      // Grant PR_REVIEW
      await setPermissions(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        username,
        ['READ', 'PULL_REQUEST_REVIEW'],
      );

      // With PR_REVIEW → 201
      const allowResp = await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/pulls/${prNumber}/comments`,
        {
          headers: { Authorization: collabAuth },
          data: { body: 'Allowed comment' },
        },
      );
      expect(allowResp.status()).toBe(201);
      const comment = await allowResp.json();
      expect(comment.body).toBe('Allowed comment');
    } finally {
      await deleteUser(bareApi, collabAuth);
    }
  });

  test('collab with PULL_REQUEST_MERGE (but not explicit REVIEW) can also comment', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const username = `scn-mergerev-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, username);

    try {
      const featureBranch = `feat-mergerev-${Date.now().toString(36)}`;
      await prepareFeatureBranch(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        featureBranch,
      );
      const prResp = await bareApi.post(`/api/repos/${owner.username}/${privateRepo.name}/pulls`, {
        headers: { Authorization: owner.authorization },
        data: {
          title: 'Merge-implies-review PR',
          description: '',
          sourceBranch: featureBranch,
          targetBranch: 'main',
          isDraft: false,
        },
      });
      const { number: prNumber } = await prResp.json();

      await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username, permissions: ['READ', 'PULL_REQUEST_MERGE'] },
      });
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        {
          headers: { Authorization: collabAuth },
        },
      );

      // MERGE implies REVIEW → comment allowed
      const commentResp = await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/pulls/${prNumber}/comments`,
        {
          headers: { Authorization: collabAuth },
          data: { body: 'Comment via MERGE permission' },
        },
      );
      expect(commentResp.status()).toBe(201);
    } finally {
      await deleteUser(bareApi, collabAuth);
    }
  });
});

// ─── SCN-COLLAB-PERM-PR-MERGE ─────────────────────────────────────────────────

test.describe('SCN-COLLAB-PERM-PR-MERGE — PULL_REQUEST_MERGE enforcement', () => {
  test('collab without PR_MERGE cannot merge; with PR_MERGE can merge', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const username = `scn-prmerge-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, username);

    try {
      const featureBranch = `feat-merge-test-${Date.now().toString(36)}`;
      await prepareFeatureBranch(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        featureBranch,
      );
      const prResp = await bareApi.post(`/api/repos/${owner.username}/${privateRepo.name}/pulls`, {
        headers: { Authorization: owner.authorization },
        data: {
          title: 'Merge Perm Test PR',
          description: '',
          sourceBranch: featureBranch,
          targetBranch: 'main',
          isDraft: false,
        },
      });
      expect(prResp.status()).toBe(201);
      const { number: prNumber } = await prResp.json();

      await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username, permissions: ['READ'] },
      });
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        {
          headers: { Authorization: collabAuth },
        },
      );

      // Without PR_MERGE → 403
      const denyResp = await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/pulls/${prNumber}/merge`,
        {
          headers: { Authorization: collabAuth },
          data: { strategy: 'MERGE_COMMIT', commitMessage: 'Denied merge' },
        },
      );
      expect(denyResp.status()).toBe(403);

      // Grant PR_MERGE
      await setPermissions(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        username,
        ['READ', 'PULL_REQUEST_MERGE'],
      );

      // With PR_MERGE → 200
      const allowResp = await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/pulls/${prNumber}/merge`,
        {
          headers: { Authorization: collabAuth },
          data: { strategy: 'MERGE_COMMIT', commitMessage: 'Allowed merge' },
        },
      );
      expect(allowResp.status()).toBe(200);
      const merged = await allowResp.json();
      expect(merged.status).toBe('MERGED');
    } finally {
      await deleteUser(bareApi, collabAuth);
    }
  });
});

// ─── SCN-COLLAB-PERM-ISSUE-MANAGE ─────────────────────────────────────────────

test.describe('SCN-COLLAB-PERM-ISSUE-MANAGE — ISSUE_MANAGE enforcement', () => {
  test('collab without ISSUE_MANAGE cannot close issue; with ISSUE_MANAGE can', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const username = `scn-issuemgr-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, username);

    try {
      // Owner creates an issue
      const issueResp = await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/issues`,
        {
          headers: { Authorization: owner.authorization },
          data: { title: 'Permission test issue', description: 'Test' },
        },
      );
      expect(issueResp.status()).toBe(201);
      const { number: issueNumber } = await issueResp.json();

      await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username, permissions: ['READ'] },
      });
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        {
          headers: { Authorization: collabAuth },
        },
      );

      // Without ISSUE_MANAGE → 403 on status change
      const denyResp = await bareApi.patch(
        `/api/repos/${owner.username}/${privateRepo.name}/issues/${issueNumber}`,
        {
          headers: { Authorization: collabAuth },
          data: { status: 'CLOSED' },
        },
      );
      expect(denyResp.status()).toBe(403);

      // Grant ISSUE_MANAGE
      await setPermissions(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        username,
        ['READ', 'ISSUE_MANAGE'],
      );

      // With ISSUE_MANAGE → 200
      const allowResp = await bareApi.patch(
        `/api/repos/${owner.username}/${privateRepo.name}/issues/${issueNumber}`,
        {
          headers: { Authorization: collabAuth },
          data: { status: 'CLOSED' },
        },
      );
      expect(allowResp.status()).toBe(200);
      const updated = await allowResp.json();
      expect(updated.status).toBe('CLOSED');
    } finally {
      await deleteUser(bareApi, collabAuth);
    }
  });

  test('collab without ISSUE_MANAGE cannot delete other user comment', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const username = `scn-cmt-deny-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, username);

    try {
      const issueResp = await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/issues`,
        {
          headers: { Authorization: owner.authorization },
          data: { title: 'Comment perm issue', description: '' },
        },
      );
      const { number: issueNumber } = await issueResp.json();

      // Owner adds a comment
      const commentResp = await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/issues/${issueNumber}/comments`,
        {
          headers: { Authorization: owner.authorization },
          data: { body: "Owner's comment" },
        },
      );
      expect(commentResp.status()).toBe(201);
      const { id: commentId } = await commentResp.json();

      await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username, permissions: ['READ'] },
      });
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        {
          headers: { Authorization: collabAuth },
        },
      );

      // Collab without ISSUE_MANAGE cannot delete owner's comment
      const denyDel = await bareApi.delete(
        `/api/repos/${owner.username}/${privateRepo.name}/issues/${issueNumber}/comments/${commentId}`,
        { headers: { Authorization: collabAuth } },
      );
      expect(denyDel.status()).toBe(403);
    } finally {
      await deleteUser(bareApi, collabAuth);
    }
  });
});

// ─── SCN-COLLAB-PERM-SETTINGS-WRITE ──────────────────────────────────────────

test.describe('SCN-COLLAB-PERM-SETTINGS-WRITE — SETTINGS_WRITE enforcement', () => {
  test('collab without SETTINGS_WRITE cannot update description; with SETTINGS_WRITE can', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const username = `scn-setwrite-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, username);

    try {
      await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username, permissions: ['READ'] },
      });
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        {
          headers: { Authorization: collabAuth },
        },
      );

      // Without SETTINGS_WRITE → 403
      const denyResp = await bareApi.patch(`/api/repo/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: collabAuth },
        data: { name: privateRepo.name, description: 'Updated by collab' },
      });
      expect(denyResp.status()).toBe(403);

      // Grant SETTINGS_WRITE
      await setPermissions(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        username,
        ['READ', 'SETTINGS_WRITE'],
      );

      // With SETTINGS_WRITE → 200
      const allowResp = await bareApi.patch(`/api/repo/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: collabAuth },
        data: { name: privateRepo.name, description: 'Updated by collab' },
      });
      expect(allowResp.status()).toBe(200);
      const updated = await allowResp.json();
      expect(updated.description).toBe('Updated by collab');
    } finally {
      await deleteUser(bareApi, collabAuth);
    }
  });

  test('collab with SETTINGS_WRITE cannot rename repo (owner-only op)', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const username = `scn-noname-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, username);

    try {
      await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username, permissions: ['READ', 'SETTINGS_WRITE'] },
      });
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        {
          headers: { Authorization: collabAuth },
        },
      );

      // Rename is owner-only → 403
      const denyRename = await bareApi.patch(`/api/repo/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: collabAuth },
        data: { name: `${privateRepo.name}-renamed` },
      });
      expect(denyRename.status()).toBe(403);
    } finally {
      await deleteUser(bareApi, collabAuth);
    }
  });
});

// ─── SCN-COLLAB-PERM-ADMIN ────────────────────────────────────────────────────

test.describe('SCN-COLLAB-PERM-ADMIN — ADMIN implies all permissions', () => {
  test('collab with ADMIN can create PR, comment, and update settings', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const username = `scn-admin-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, username);

    try {
      const featureBranch = `feat-admin-test-${Date.now().toString(36)}`;
      await prepareFeatureBranch(
        bareApi,
        owner.authorization,
        owner.username,
        privateRepo.name,
        featureBranch,
      );

      await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username, permissions: ['ADMIN'] },
      });
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        {
          headers: { Authorization: collabAuth },
        },
      );

      // Can create PR (PULL_REQUEST_CREATE implied by ADMIN)
      const prResp = await bareApi.post(`/api/repos/${owner.username}/${privateRepo.name}/pulls`, {
        headers: { Authorization: collabAuth },
        data: {
          title: 'Admin creates PR',
          description: '',
          sourceBranch: featureBranch,
          targetBranch: 'main',
          isDraft: false,
        },
      });
      expect(prResp.status()).toBe(201);
      const { number: prNumber } = await prResp.json();

      // Can comment on PR (PULL_REQUEST_REVIEW implied by ADMIN)
      const commentResp = await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/pulls/${prNumber}/comments`,
        {
          headers: { Authorization: collabAuth },
          data: { body: 'Admin comment' },
        },
      );
      expect(commentResp.status()).toBe(201);

      // Can update description (SETTINGS_WRITE implied by ADMIN)
      const settingsResp = await bareApi.patch(`/api/repo/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: collabAuth },
        data: { name: privateRepo.name, description: 'Admin updated description' },
      });
      expect(settingsResp.status()).toBe(200);

      // Can create issue + update status (ISSUE_MANAGE implied by ADMIN)
      const issueResp = await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/issues`,
        {
          headers: { Authorization: collabAuth },
          data: { title: 'Admin issue', description: '' },
        },
      );
      expect(issueResp.status()).toBe(201);
      const { number: issueNumber } = await issueResp.json();

      const toggleResp = await bareApi.patch(
        `/api/repos/${owner.username}/${privateRepo.name}/issues/${issueNumber}`,
        {
          headers: { Authorization: collabAuth },
          data: { status: 'CLOSED' },
        },
      );
      expect(toggleResp.status()).toBe(200);
    } finally {
      await deleteUser(bareApi, collabAuth);
    }
  });
});
