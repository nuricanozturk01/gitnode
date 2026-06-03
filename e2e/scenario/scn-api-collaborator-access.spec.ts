/**
 * SCN-API-COLLABORATOR — full collaborator lifecycle on private repositories.
 *
 * Covers: invite → accept → access → permission-check → remove → revoked-access.
 * Also covers: decline flow, self-remove, profile visibility of private repos.
 */
import { expect, test } from './fixtures/scenario';
import { deleteUser, registerAndLogin } from './helpers/register-and-login';

function repoCollaboratorsApi(owner: string, repo: string): string {
  return `/api/repos/${owner}/${repo}/collaborators`;
}

test.describe('SCN-API-COLLABORATOR — invite and access flow', () => {
  test('full collaborator lifecycle: invite → accept → access → remove → deny', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const collaboratorUsername = `scn-collab-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, collaboratorUsername);

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
            username: collaboratorUsername,
            permissions: ['READ', 'PUSH', 'PULL_REQUEST_REVIEW'],
          },
        },
      );
      expect(inviteResp.status()).toBe(201);
      const invite = await inviteResp.json();
      expect(invite.status).toBe('PENDING');
      expect(invite.username).toBe(collaboratorUsername);

      // Step 3: Collaborator still cannot access repo while PENDING
      const pendingAccess = await bareApi.get(`/api/repos/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: collabAuth },
      });
      expect(pendingAccess.status()).toBe(403);

      // Step 4: Collaborator accepts invitation
      const acceptResp = await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        { headers: { Authorization: collabAuth } },
      );
      expect(acceptResp.status()).toBe(200);
      expect((await acceptResp.json()).status).toBe('ACCEPTED');

      // Step 5: Collaborator can now access the private repo
      const accessResp = await bareApi.get(`/api/repo/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: collabAuth },
      });
      expect(accessResp.status()).toBe(200);
      const repoBody = await accessResp.json();
      expect(repoBody.name).toBe(privateRepo.name);

      // Step 6: Collaborator can list collaborators (they have access)
      const listResp = await bareApi.get(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: collabAuth },
      });
      expect(listResp.status()).toBe(200);
      const listBody = await listResp.json();
      const list: { username: string }[] = Array.isArray(listBody) ? listBody : listBody.content;
      expect(list.some((c) => c.username === collaboratorUsername)).toBe(true);

      // Step 7: Owner removes collaborator
      const removeResp = await bareApi.delete(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/${collaboratorUsername}`,
        { headers: { Authorization: owner.authorization } },
      );
      expect(removeResp.status()).toBe(204);

      // Step 8: After removal, collaborator cannot access the private repo
      const revokedAccess = await bareApi.get(`/api/repos/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: collabAuth },
      });
      expect(revokedAccess.status()).toBe(403);
    } finally {
      await deleteUser(bareApi, collabAuth);
    }
  });

  test('SCN-COLLABORATOR-DECLINE — declined invitee loses access', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const collaboratorUsername = `scn-decline-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, collaboratorUsername);

    try {
      // Invite
      const inviteResp = await bareApi.post(
        repoCollaboratorsApi(owner.username, privateRepo.name),
        {
          headers: { Authorization: owner.authorization },
          data: { username: collaboratorUsername, permissions: ['READ'] },
        },
      );
      expect(inviteResp.status()).toBe(201);

      // Decline
      const declineResp = await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/decline`,
        { headers: { Authorization: collabAuth } },
      );
      expect(declineResp.status()).toBe(200);
      expect((await declineResp.json()).status).toBe('DECLINED');

      // Still cannot access
      const accessResp = await bareApi.get(`/api/repos/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: collabAuth },
      });
      expect(accessResp.status()).toBe(403);

      // Can be re-invited after decline
      const reInviteResp = await bareApi.post(
        repoCollaboratorsApi(owner.username, privateRepo.name),
        {
          headers: { Authorization: owner.authorization },
          data: { username: collaboratorUsername, permissions: ['READ'] },
        },
      );
      expect(reInviteResp.status()).toBe(201);
      expect((await reInviteResp.json()).status).toBe('PENDING');
    } finally {
      await deleteUser(bareApi, collabAuth);
    }
  });

  test('SCN-COLLABORATOR-SELF-REMOVE — collaborator can remove themselves', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const collaboratorUsername = `scn-selfrem-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, collaboratorUsername);

    try {
      // Invite and accept
      await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username: collaboratorUsername, permissions: ['READ'] },
      });
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        { headers: { Authorization: collabAuth } },
      );

      // Self-remove
      const selfRemoveResp = await bareApi.delete(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/${collaboratorUsername}`,
        { headers: { Authorization: collabAuth } },
      );
      expect(selfRemoveResp.status()).toBe(204);

      // No longer has access
      const accessResp = await bareApi.get(`/api/repos/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: collabAuth },
      });
      expect(accessResp.status()).toBe(403);
    } finally {
      await deleteUser(bareApi, collabAuth);
    }
  });

  test('SCN-COLLABORATOR-PROFILE-VISIBILITY — private repo visible in owner profile for accepted collaborator', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const collaboratorUsername = `scn-profvis-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, collaboratorUsername);

    try {
      // Before invitation: collaborator sees only public repos count
      const beforeList = await bareApi.get(`/api/repo/${owner.username}`, {
        headers: { Authorization: collabAuth },
        params: { page: '0', size: '50' },
      });
      const _beforeCount = beforeList.ok() ? (await beforeList.json()).totalElements : 0;

      // Invite and accept
      await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username: collaboratorUsername, permissions: ['READ'] },
      });
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        { headers: { Authorization: collabAuth } },
      );

      // After acceptance: private repo appears in listing
      const afterList = await bareApi.get(`/api/repo/${owner.username}`, {
        headers: { Authorization: collabAuth },
        params: { page: '0', size: '50' },
      });
      expect(afterList.status()).toBe(200);
      const afterBody = await afterList.json();
      const repoNames = afterBody.content.map((r: { name: string }) => r.name);
      expect(repoNames).toContain(privateRepo.name);
    } finally {
      await deleteUser(bareApi, collabAuth);
    }
  });

  test('SCN-COLLABORATOR-PERMISSIONS — only owner can update permissions', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }) => {
    const collaboratorUsername = `scn-perms-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, collaboratorUsername);

    try {
      // Invite and accept
      await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username: collaboratorUsername, permissions: ['READ'] },
      });
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        { headers: { Authorization: collabAuth } },
      );

      // Intruder cannot update permissions
      const intruderUpdateResp = await bareApi.put(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/${collaboratorUsername}/permissions`,
        {
          headers: { Authorization: intruder.authorization },
          data: { permissions: ['READ', 'ADMIN'] },
        },
      );
      expect([403, 401]).toContain(intruderUpdateResp.status());

      // Owner can update permissions
      const ownerUpdateResp = await bareApi.put(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/${collaboratorUsername}/permissions`,
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
      await deleteUser(bareApi, collabAuth);
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
    privateRepo,
  }) => {
    const collaboratorUsername = `scn-dblaccept-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, collaboratorUsername);

    try {
      await bareApi.post(repoCollaboratorsApi(owner.username, privateRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username: collaboratorUsername, permissions: ['READ'] },
      });
      await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        { headers: { Authorization: collabAuth } },
      );

      // Try to accept again
      const doubleAccept = await bareApi.post(
        `${repoCollaboratorsApi(owner.username, privateRepo.name)}/invitation/accept`,
        { headers: { Authorization: collabAuth } },
      );
      expect(doubleAccept.status()).toBe(400);
    } finally {
      await deleteUser(bareApi, collabAuth);
    }
  });

  test('SCN-COLLABORATOR-PUBLIC-REPO — public repo collaborators listed by owner', async ({
    bareApi,
    owner,
    publicRepo,
  }) => {
    const collaboratorUsername = `scn-pubcollab-${Date.now().toString(36)}`;
    const { authorization: collabAuth } = await registerAndLogin(bareApi, collaboratorUsername);

    try {
      const inviteResp = await bareApi.post(repoCollaboratorsApi(owner.username, publicRepo.name), {
        headers: { Authorization: owner.authorization },
        data: { username: collaboratorUsername, permissions: ['READ', 'PUSH'] },
      });
      expect(inviteResp.status()).toBe(201);

      const listResp = await bareApi.get(repoCollaboratorsApi(owner.username, publicRepo.name), {
        headers: { Authorization: owner.authorization },
      });
      expect(listResp.status()).toBe(200);
      const listBody = await listResp.json();
      const list: { username: string }[] = Array.isArray(listBody) ? listBody : listBody.content;
      expect(list.some((c) => c.username === collaboratorUsername)).toBe(true);
    } finally {
      await deleteUser(bareApi, collabAuth);
    }
  });
});
