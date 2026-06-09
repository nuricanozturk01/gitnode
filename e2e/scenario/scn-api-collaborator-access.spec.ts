/**
 * SCN-API-COLLABORATOR — full collaborator lifecycle on private repositories.
 *
 * Covers: invite → accept → access → permission-check → remove → revoked-access.
 * Also covers: decline flow, self-remove, profile visibility of private repos.
 *
 * All tests use the shared intruder user instead of registering ephemeral accounts.
 * Each test creates its own repo so parallel execution is safe.
 */
import { expect, test } from './fixtures/scenario';

function repoCollaboratorsApi(owner: string, repo: string): string {
  return `/api/repos/${owner}/${repo}/collaborators`;
}

test.describe('SCN-API-COLLABORATOR — invite and access flow', () => {
  test('full collaborator lifecycle: invite → accept → access → remove → deny', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }) => {
    try {
      // Step 1: Guest cannot access private repo
      const guestAccess = await bareApi.get(`/api/repos/${owner.username}/${privateRepo.name}`);
      expect(guestAccess.status()).toBe(403);

      // Step 2: Owner invites collaborator
      const inviteResp = await bareApi.post(
        repoCollaboratorsApi(owner.username, privateRepo.name),
        {
          headers: { Authorization: owner.authorization },
          data: {
            username: intruder.username,
            permissions: ['READ', 'PUSH', 'PULL_REQUEST_REVIEW'],
          },
        },
      );
      expect(inviteResp.status()).toBe(201);
      const invite = await inviteResp.json();
      expect(invite.status).toBe('PENDING');
      expect(invite.username).toBe(intruder.username);

      // Step 3: Collaborator still cannot access repo while PENDING
      const pendingAccess = await bareApi.get(`/api/repos/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: intruder.authorization },
      });
      expect(pendingAccess.status()).toBe(403);

      // Step 4: Collaborator accepts invitation
      const acceptResp = await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        { headers: { Authorization: intruder.authorization } },
      );
      expect(acceptResp.status()).toBe(200);
      expect((await acceptResp.json()).status).toBe('ACCEPTED');

      // Step 5: Collaborator can now access the private repo
      const accessResp = await bareApi.get(`/api/repo/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: intruder.authorization },
      });
      expect(accessResp.status()).toBe(200);
      const repoBody = await accessResp.json();
      expect(repoBody.name).toBe(privateRepo.name);

      // Step 6: Collaborator can list collaborators (they have access)
      const listResp = await bareApi.get(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: intruder.authorization },
      });
      expect(listResp.status()).toBe(200);
      const listBody = await listResp.json();
      const list: { username: string }[] = Array.isArray(listBody) ? listBody : listBody.content;
      expect(list.some((c) => c.username === intruder.username)).toBe(true);

      // Step 7: Owner removes collaborator
      const removeResp = await bareApi.delete(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/${intruder.username}`,
        { headers: { Authorization: owner.authorization } },
      );
      expect(removeResp.status()).toBe(204);

      // Step 8: After removal, collaborator cannot access the private repo
      const revokedAccess = await bareApi.get(`/api/repos/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: intruder.authorization },
      });
      expect(revokedAccess.status()).toBe(403);
    } finally {
      await bareApi
        .delete(`${repoCollaboratorsApi(owner.username, privateRepo.name)}/${intruder.username}`, {
          headers: { Authorization: owner.authorization },
        })
        .catch(() => {});
    }
  });

  test('SCN-COLLABORATOR-DECLINE — declined invitee loses access', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }) => {
    try {
      // Invite
      const inviteResp = await bareApi.post(
        repoCollaboratorsApi(owner.username, privateRepo.name),
        {
          headers: { Authorization: owner.authorization },
          data: { username: intruder.username, permissions: ['READ'] },
        },
      );
      expect(inviteResp.status()).toBe(201);

      // Decline
      const declineResp = await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/decline`,
        { headers: { Authorization: intruder.authorization } },
      );
      expect(declineResp.status()).toBe(200);
      expect((await declineResp.json()).status).toBe('DECLINED');

      // Still cannot access
      const accessResp = await bareApi.get(`/api/repos/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: intruder.authorization },
      });
      expect(accessResp.status()).toBe(403);

      // Can be re-invited after decline
      const reInviteResp = await bareApi.post(
        repoCollaboratorsApi(owner.username, privateRepo.name),
        {
          headers: { Authorization: owner.authorization },
          data: { username: intruder.username, permissions: ['READ'] },
        },
      );
      expect(reInviteResp.status()).toBe(201);
      expect((await reInviteResp.json()).status).toBe('PENDING');
    } finally {
      await bareApi
        .delete(`${repoCollaboratorsApi(owner.username, privateRepo.name)}/${intruder.username}`, {
          headers: { Authorization: owner.authorization },
        })
        .catch(() => {});
    }
  });

  test('SCN-COLLABORATOR-SELF-REMOVE — collaborator can remove themselves', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }) => {
    // Invite and accept
    await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
      headers: { Authorization: owner.authorization },
      data: { username: intruder.username, permissions: ['READ'] },
    });
    await bareApi.post(
      `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
      { headers: { Authorization: intruder.authorization } },
    );

    // Self-remove
    const selfRemoveResp = await bareApi.delete(
      `${repoCollaboratorsApi(owner.username, privateRepo.name)}/${intruder.username}`,
      { headers: { Authorization: intruder.authorization } },
    );
    expect(selfRemoveResp.status()).toBe(204);

    // No longer has access
    const accessResp = await bareApi.get(`/api/repos/${owner.username}/${privateRepo.name}`, {
      headers: { Authorization: intruder.authorization },
    });
    expect(accessResp.status()).toBe(403);
  });

  test('SCN-COLLABORATOR-PROFILE-VISIBILITY — private repo visible in owner profile for accepted collaborator', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }) => {
    try {
      // Invite and accept
      await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username: intruder.username, permissions: ['READ'] },
      });
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        { headers: { Authorization: intruder.authorization } },
      );

      // After acceptance: private repo appears in listing
      const afterList = await bareApi.get(`/api/repo/${owner.username}`, {
        headers: { Authorization: intruder.authorization },
        params: { page: '0', size: '50' },
      });
      expect(afterList.status()).toBe(200);
      const afterBody = await afterList.json();
      const repoNames = afterBody.content.map((r: { name: string }) => r.name);
      expect(repoNames).toContain(privateRepo.name);
    } finally {
      await bareApi
        .delete(`${repoCollaboratorsApi(owner.username, privateRepo.name)}/${intruder.username}`, {
          headers: { Authorization: owner.authorization },
        })
        .catch(() => {});
    }
  });

  test('SCN-COLLABORATOR-PERMISSIONS — only owner can update permissions', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }) => {
    try {
      // Invite intruder and accept
      await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username: intruder.username, permissions: ['READ'] },
      });
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        { headers: { Authorization: intruder.authorization } },
      );

      // Collaborator cannot update their own permissions
      const collabUpdateResp = await bareApi.put(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/${intruder.username}/permissions`,
        {
          headers: { Authorization: intruder.authorization },
          data: { permissions: ['READ', 'ADMIN'] },
        },
      );
      expect([403, 401]).toContain(collabUpdateResp.status());

      // Owner can update permissions
      const ownerUpdateResp = await bareApi.put(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/${intruder.username}/permissions`,
        {
          headers: { Authorization: owner.authorization },
          data: { permissions: ['READ', 'PULL_REQUEST_MERGE', 'SETTINGS_READ'] },
        },
      );
      expect(ownerUpdateResp.status()).toBe(200);
      const updated = await ownerUpdateResp.json();
      expect(updated.permissions).toContain('PULL_REQUEST_MERGE');
      expect(updated.permissions).toContain('SETTINGS_READ');
    } finally {
      await bareApi
        .delete(`${repoCollaboratorsApi(owner.username, privateRepo.name)}/${intruder.username}`, {
          headers: { Authorization: owner.authorization },
        })
        .catch(() => {});
    }
  });

  test('SCN-COLLABORATOR-CANNOT-INVITE-SELF — owner cannot invite themselves', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const selfInviteResp = await bareApi.post(
      repoCollaboratorsApi(owner.username, privateRepo.name),
      {
        headers: { Authorization: owner.authorization },
        data: { username: owner.username, permissions: ['READ'] },
      },
    );
    expect(selfInviteResp.status()).toBe(400);
  });

  test('SCN-COLLABORATOR-DOUBLE-ACCEPT — cannot accept already-accepted invitation', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }) => {
    try {
      await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username: intruder.username, permissions: ['READ'] },
      });
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        { headers: { Authorization: intruder.authorization } },
      );

      // Try to accept again
      const doubleAccept = await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        { headers: { Authorization: intruder.authorization } },
      );
      expect(doubleAccept.status()).toBe(400);
    } finally {
      await bareApi
        .delete(`${repoCollaboratorsApi(owner.username, privateRepo.name)}/${intruder.username}`, {
          headers: { Authorization: owner.authorization },
        })
        .catch(() => {});
    }
  });

  test('SCN-COLLABORATOR-PUBLIC-REPO — public repo collaborators listed by owner', async ({
    bareApi,
    owner,
    intruder,
    publicRepo,
  }) => {
    try {
      const inviteResp = await bareApi.post(repoCollaboratorsApi(owner.username, publicRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username: intruder.username, permissions: ['READ', 'PUSH'] },
      });
      expect(inviteResp.status()).toBe(201);

      const listResp = await bareApi.get(repoCollaboratorsApi(owner.username, publicRepo.name), {
        headers: { Authorization: owner.authorization },
      });
      expect(listResp.status()).toBe(200);
      const listBody = await listResp.json();
      const list: { username: string }[] = Array.isArray(listBody) ? listBody : listBody.content;
      expect(list.some((c) => c.username === intruder.username)).toBe(true);
    } finally {
      await bareApi
        .delete(`${repoCollaboratorsApi(owner.username, publicRepo.name)}/${intruder.username}`, {
          headers: { Authorization: owner.authorization },
        })
        .catch(() => {});
    }
  });
});
