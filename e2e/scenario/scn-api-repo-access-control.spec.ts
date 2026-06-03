/**
 * SCN-API-REPO-ACCESS — repository visibility access control.
 *
 * Private: only owner + accepted collaborators can access.
 * Public: anyone can read; write operations still require auth + ownership.
 */
import { expect, test } from './fixtures/scenario';
import { deleteUser, registerAndLogin } from './helpers/register-and-login';

// ──────────────────────────────────────────────────────────────────────────────
// PRIVATE REPO ACCESS
// ──────────────────────────────────────────────────────────────────────────────

test.describe('SCN-API-PRIVATE-REPO — access control', () => {
  test('owner can read all private repo endpoints', async ({ bareApi, owner, privateRepo }) => {
    const repoResp = await bareApi.get(`/api/repo/${owner.username}/${privateRepo.name}`, {
      headers: { Authorization: owner.authorization },
    });
    expect(repoResp.status()).toBe(200);

    const branchResp = await bareApi.get(
      `/api/repos/${owner.username}/${privateRepo.name}/branches`,
      { headers: { Authorization: owner.authorization } },
    );
    expect(branchResp.status()).toBe(200);

    const commitResp = await bareApi.get(
      `/api/repos/${owner.username}/${privateRepo.name}/commits`,
      {
        headers: { Authorization: owner.authorization },
        params: { branch: privateRepo.defaultBranch, page: '0', size: '10' },
      },
    );
    expect(commitResp.status()).toBe(200);
  });

  test('unauthenticated guest is denied private repo', async ({ bareApi, owner, privateRepo }) => {
    const repoResp = await bareApi.get(`/api/repos/${owner.username}/${privateRepo.name}`);
    expect(repoResp.status()).toBe(403);

    const branchResp = await bareApi.get(
      `/api/repos/${owner.username}/${privateRepo.name}/branches`,
    );
    expect(branchResp.status()).toBe(403);
  });

  test('registered non-collaborator is denied private repo', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }) => {
    const repoResp = await bareApi.get(`/api/repos/${owner.username}/${privateRepo.name}`, {
      headers: { Authorization: intruder.authorization },
    });
    expect(repoResp.status()).toBe(403);

    const branchResp = await bareApi.get(
      `/api/repos/${owner.username}/${privateRepo.name}/branches`,
      { headers: { Authorization: intruder.authorization } },
    );
    expect(branchResp.status()).toBe(403);
  });

  test('pending collaborator is denied private repo before accepting', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const username = `scn-pending-${Date.now().toString(36)}`;
    const { authorization } = await registerAndLogin(bareApi, username);

    try {
      await bareApi.post(`/api/repos/${owner.username}/${privateRepo.name}/collaborators`, {
        headers: { Authorization: owner.authorization },
        data: { username, permissions: ['READ'] },
      });

      const repoResp = await bareApi.get(`/api/repos/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: authorization },
      });
      expect(repoResp.status()).toBe(403);
    } finally {
      await deleteUser(bareApi, authorization);
    }
  });

  test('accepted collaborator can access private repo', async ({ bareApi, owner, privateRepo }) => {
    const username = `scn-accepted-${Date.now().toString(36)}`;
    const { authorization } = await registerAndLogin(bareApi, username);

    try {
      await bareApi.post(`/api/repos/${owner.username}/${privateRepo.name}/collaborators`, {
        headers: { Authorization: owner.authorization },
        data: { username, permissions: ['READ'] },
      });
      await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/collaborators/invitation/accept`,
        { headers: { Authorization: authorization } },
      );

      const repoResp = await bareApi.get(`/api/repo/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: authorization },
      });
      expect(repoResp.status()).toBe(200);
    } finally {
      await deleteUser(bareApi, authorization);
    }
  });

  test('declined collaborator is denied private repo', async ({ bareApi, owner, privateRepo }) => {
    const username = `scn-declined-${Date.now().toString(36)}`;
    const { authorization } = await registerAndLogin(bareApi, username);

    try {
      await bareApi.post(`/api/repos/${owner.username}/${privateRepo.name}/collaborators`, {
        headers: { Authorization: owner.authorization },
        data: { username, permissions: ['READ'] },
      });
      await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/collaborators/invitation/decline`,
        { headers: { Authorization: authorization } },
      );

      const repoResp = await bareApi.get(`/api/repos/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: authorization },
      });
      expect(repoResp.status()).toBe(403);
    } finally {
      await deleteUser(bareApi, authorization);
    }
  });

  test('removed collaborator is denied private repo after removal', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const username = `scn-removed-${Date.now().toString(36)}`;
    const { authorization } = await registerAndLogin(bareApi, username);

    try {
      await bareApi.post(`/api/repos/${owner.username}/${privateRepo.name}/collaborators`, {
        headers: { Authorization: owner.authorization },
        data: { username, permissions: ['READ'] },
      });
      await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/collaborators/invitation/accept`,
        { headers: { Authorization: authorization } },
      );
      // Verify access before removal
      const beforeResp = await bareApi.get(`/api/repo/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: authorization },
      });
      expect(beforeResp.status()).toBe(200);

      await bareApi.delete(
        `/api/repos/${owner.username}/${privateRepo.name}/collaborators/${username}`,
        { headers: { Authorization: owner.authorization } },
      );

      const afterResp = await bareApi.get(`/api/repos/${owner.username}/${privateRepo.name}`, {
        headers: { Authorization: authorization },
      });
      expect(afterResp.status()).toBe(403);
    } finally {
      await deleteUser(bareApi, authorization);
    }
  });

  test('private repo does not appear in owner profile listing for non-collaborator', async ({
    bareApi,
    owner,
    intruder,
    privateRepo,
  }) => {
    const listResp = await bareApi.get(`/api/repo/${owner.username}`, {
      headers: { Authorization: intruder.authorization },
      params: { page: '0', size: '50' },
    });
    expect(listResp.status()).toBe(200);
    const body = await listResp.json();
    const names: string[] = body.content.map((r: { name: string }) => r.name);
    expect(names).not.toContain(privateRepo.name);
  });

  test('private repo appears in owner profile listing for accepted collaborator', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const username = `scn-proflist-${Date.now().toString(36)}`;
    const { authorization } = await registerAndLogin(bareApi, username);

    try {
      await bareApi.post(`/api/repos/${owner.username}/${privateRepo.name}/collaborators`, {
        headers: { Authorization: owner.authorization },
        data: { username, permissions: ['READ'] },
      });
      await bareApi.post(
        `/api/repos/${owner.username}/${privateRepo.name}/collaborators/invitation/accept`,
        { headers: { Authorization: authorization } },
      );

      const listResp = await bareApi.get(`/api/repo/${owner.username}`, {
        headers: { Authorization: authorization },
        params: { page: '0', size: '50' },
      });
      expect(listResp.status()).toBe(200);
      const body = await listResp.json();
      const names: string[] = body.content.map((r: { name: string }) => r.name);
      expect(names).toContain(privateRepo.name);
    } finally {
      await deleteUser(bareApi, authorization);
    }
  });
});

// ──────────────────────────────────────────────────────────────────────────────
// PUBLIC REPO ACCESS
// ──────────────────────────────────────────────────────────────────────────────

test.describe('SCN-API-PUBLIC-REPO — access control', () => {
  test('unauthenticated guest can read public repo', async ({ bareApi, owner, publicRepo }) => {
    const repoResp = await bareApi.get(`/api/repo/${owner.username}/${publicRepo.name}`);
    expect(repoResp.status()).toBe(200);
    expect((await repoResp.json()).name).toBe(publicRepo.name);
  });

  test('any registered user can read public repo', async ({
    bareApi,
    owner,
    intruder,
    publicRepo,
  }) => {
    const repoResp = await bareApi.get(`/api/repo/${owner.username}/${publicRepo.name}`, {
      headers: { Authorization: intruder.authorization },
    });
    expect(repoResp.status()).toBe(200);

    const branchResp = await bareApi.get(
      `/api/repos/${owner.username}/${publicRepo.name}/branches`,
      { headers: { Authorization: intruder.authorization } },
    );
    expect(branchResp.status()).toBe(200);

    const commitResp = await bareApi.get(
      `/api/repos/${owner.username}/${publicRepo.name}/commits`,
      {
        headers: { Authorization: intruder.authorization },
        params: { branch: publicRepo.defaultBranch, page: '0', size: '10' },
      },
    );
    expect(commitResp.status()).toBe(200);
  });

  test('public repo appears in owner profile listing for everyone', async ({
    bareApi,
    owner,
    intruder,
    publicRepo,
  }) => {
    const guestList = await bareApi.get(`/api/repo/${owner.username}`, {
      params: { page: '0', size: '50' },
    });
    expect(guestList.status()).toBe(200);
    const guestNames: string[] = (await guestList.json()).content.map(
      (r: { name: string }) => r.name,
    );
    expect(guestNames).toContain(publicRepo.name);

    const intruderList = await bareApi.get(`/api/repo/${owner.username}`, {
      headers: { Authorization: intruder.authorization },
      params: { page: '0', size: '50' },
    });
    const intruderNames: string[] = (await intruderList.json()).content.map(
      (r: { name: string }) => r.name,
    );
    expect(intruderNames).toContain(publicRepo.name);
  });

  test('non-owner cannot delete public repo', async ({ bareApi, owner, intruder, publicRepo }) => {
    const deleteResp = await bareApi.delete(`/api/repo/${owner.username}/${publicRepo.name}`, {
      headers: { Authorization: intruder.authorization },
    });
    expect([403, 401]).toContain(deleteResp.status());
  });

  test('non-owner cannot push to public repo via settings update', async ({
    bareApi,
    owner,
    intruder,
    publicRepo,
  }) => {
    const patchResp = await bareApi.patch(`/api/repo/${owner.username}/${publicRepo.name}`, {
      headers: { Authorization: intruder.authorization },
      data: { name: publicRepo.name, description: 'Hacked' },
    });
    expect([403, 401]).toContain(patchResp.status());
  });
});
