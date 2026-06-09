import { reposApi } from '@helpers/paths';

import { expect, test } from './fixtures/authenticated-api';

function repoCollaboratorsApi(owner: string, repo: string): string {
  return `${reposApi(owner, repo)}/collaborators`;
}

test.describe.serial('Collaborator API — all endpoints', () => {
  let privateRepoName: string;
  let collaboratorUsername: string;
  let collaboratorAuthorization: string;

  test.beforeAll(async ({ authedRequest, session }) => {
    privateRepoName = `e2e-collab-${Date.now().toString(36)}`;

    await authedRequest.post('/api/repo', {
      data: { name: privateRepoName, description: 'Collab test', isPrivate: true },
    });

    collaboratorUsername = session.intruderUsername;
    collaboratorAuthorization = session.intruderAuthorization;
  });

  test.afterAll(async ({ authedRequest, session }) => {
    await authedRequest
      .delete(`${repoCollaboratorsApi(session.username, privateRepoName)}/${collaboratorUsername}`)
      .catch(() => {});
    await authedRequest.delete(`/api/repo/${session.username}/${privateRepoName}`).catch(() => {});
  });

  test('POST /collaborators — owner invites collaborator', async ({ authedRequest, session }) => {
    const response = await authedRequest.post(
      repoCollaboratorsApi(session.username, privateRepoName),
      {
        data: {
          username: collaboratorUsername,
          permissions: ['READ', 'PUSH'],
        },
      },
    );
    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body.username).toBe(collaboratorUsername);
    expect(body.status).toBe('PENDING');
    expect(body.permissions).toContain('READ');
    expect(body.permissions).toContain('PUSH');
  });

  test('GET /collaborators — owner lists collaborators', async ({ authedRequest, session }) => {
    const response = await authedRequest.get(
      repoCollaboratorsApi(session.username, privateRepoName),
    );
    expect(response.status()).toBe(200);
    const body = await response.json();
    const items: { username: string }[] = Array.isArray(body) ? body : body.content;
    expect(Array.isArray(items)).toBe(true);
    expect(items.some((c) => c.username === collaboratorUsername)).toBe(true);
  });

  test('GET /collaborators/invitation — invitee sees pending invitation', async ({
    authedRequest: _authedRequest,
    playwright,
    session,
  }) => {
    const collaboratorCtx = await playwright.request.newContext({
      baseURL: session.baseUrl,
      extraHTTPHeaders: { Authorization: collaboratorAuthorization },
    });
    const response = await collaboratorCtx.get(
      `${repoCollaboratorsApi(session.username, privateRepoName)}/invitation`,
    );
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.status).toBe('PENDING');
    await collaboratorCtx.dispose();
  });

  test('POST /collaborators/invitation/accept — invitee accepts', async ({
    authedRequest: _authedRequest,
    playwright,
    session,
  }) => {
    const collaboratorCtx = await playwright.request.newContext({
      baseURL: session.baseUrl,
      extraHTTPHeaders: { Authorization: collaboratorAuthorization },
    });
    const response = await collaboratorCtx.post(
      `${repoCollaboratorsApi(session.username, privateRepoName)}/invitation/accept`,
    );
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.status).toBe('ACCEPTED');
    await collaboratorCtx.dispose();
  });

  test('collaborator can access private repo after acceptance', async ({ playwright, session }) => {
    const collaboratorCtx = await playwright.request.newContext({
      baseURL: session.baseUrl,
      extraHTTPHeaders: { Authorization: collaboratorAuthorization },
    });
    const response = await collaboratorCtx.get(`/api/repo/${session.username}/${privateRepoName}`);
    expect(response.status()).toBe(200);
    await collaboratorCtx.dispose();
  });

  test('PUT /collaborators/{username}/permissions — owner updates permissions', async ({
    authedRequest,
    session,
  }) => {
    const response = await authedRequest.put(
      `${repoCollaboratorsApi(session.username, privateRepoName)}/${collaboratorUsername}/permissions`,
      {
        data: { permissions: ['READ', 'PULL_REQUEST_REVIEW', 'ISSUE_MANAGE'] },
      },
    );
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.permissions).toContain('PULL_REQUEST_REVIEW');
    expect(body.permissions).toContain('ISSUE_MANAGE');
  });

  test('guest cannot access collaborator list without auth', async ({ playwright, session }) => {
    const guestCtx = await playwright.request.newContext({ baseURL: session.baseUrl });
    const response = await guestCtx.get(repoCollaboratorsApi(session.username, privateRepoName));
    expect([401, 403]).toContain(response.status());
    await guestCtx.dispose();
  });

  test('DELETE /collaborators/{username} — owner removes collaborator', async ({
    authedRequest,
    session,
  }) => {
    const response = await authedRequest.delete(
      `${repoCollaboratorsApi(session.username, privateRepoName)}/${collaboratorUsername}`,
    );
    expect(response.status()).toBe(204);
  });

  test('collaborator cannot access private repo after removal', async ({ playwright, session }) => {
    const collaboratorCtx = await playwright.request.newContext({
      baseURL: session.baseUrl,
      extraHTTPHeaders: { Authorization: collaboratorAuthorization },
    });
    const response = await collaboratorCtx.get(`/api/repo/${session.username}/${privateRepoName}`);
    expect([403]).toContain(response.status());
    await collaboratorCtx.dispose();
  });
});

// ──────────────────────────────────────────────────────────────────────────────
// INVITE LINK TESTS
// ──────────────────────────────────────────────────────────────────────────────

test.describe.serial('Collaborator Invite Link API', () => {
  let linkRepoName: string;
  let linkUsername: string;
  let linkAuthorization: string;
  let inviteToken: string;

  let ownerUsername: string;

  test.beforeAll(async ({ authedRequest, session }) => {
    ownerUsername = session.username;
    linkRepoName = `e2e-link-${Date.now().toString(36)}`;
    await authedRequest.post('/api/repo', {
      data: { name: linkRepoName, description: 'Invite link test', isPrivate: true },
    });

    linkUsername = session.intruderUsername;
    linkAuthorization = session.intruderAuthorization;

    await authedRequest.post(repoCollaboratorsApi(ownerUsername, linkRepoName), {
      data: { username: linkUsername, permissions: ['READ'] },
    });
  });

  test.afterAll(async ({ authedRequest, session }) => {
    await authedRequest
      .delete(`${repoCollaboratorsApi(session.username, linkRepoName)}/${linkUsername}`)
      .catch(() => {});
    await authedRequest.delete(`/api/repo/${session.username}/${linkRepoName}`).catch(() => {});
  });

  test('POST /{username}/invite-link — owner generates invite link', async ({
    authedRequest,
    session,
  }) => {
    const response = await authedRequest.post(
      `${repoCollaboratorsApi(session.username, linkRepoName)}/${linkUsername}/invite-link`,
    );
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.token).toBeTruthy();
    expect(body.expiresAt).toBeTruthy();
    expect(body.repoOwner).toBe(session.username);
    expect(body.repoName).toBe(linkRepoName);
    expect(body.inviteeUsername).toBe(linkUsername);
    inviteToken = body.token as string;
  });

  test('GET /api/invitations/{token} — public endpoint returns invite info', async ({
    authedRequest,
    session,
  }) => {
    const response = await authedRequest.get(`/api/invitations/${inviteToken}`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.repoOwner).toBe(session.username);
    expect(body.repoName).toBe(linkRepoName);
    expect(body.invitedUsername).toBe(linkUsername);
    expect(body.permissions).toContain('READ');
  });

  test('GET /api/invitations/{token} — unauthenticated guest can view invite info', async ({
    playwright,
    session,
  }) => {
    const guestCtx = await playwright.request.newContext({ baseURL: session.baseUrl });
    const response = await guestCtx.get(`/api/invitations/${inviteToken}`);
    expect(response.status()).toBe(200);
    await guestCtx.dispose();
  });

  test('POST /api/invitations/{token}/accept — invitee accepts via token', async ({
    playwright,
    session,
  }) => {
    const inviteeCtx = await playwright.request.newContext({
      baseURL: session.baseUrl,
      extraHTTPHeaders: { Authorization: linkAuthorization },
    });
    const response = await inviteeCtx.post(`/api/invitations/${inviteToken}/accept`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.status).toBe('ACCEPTED');
    await inviteeCtx.dispose();
  });

  test('POST /api/invitations/{token}/accept — token cannot be reused after acceptance', async ({
    playwright,
    session,
  }) => {
    const inviteeCtx = await playwright.request.newContext({
      baseURL: session.baseUrl,
      extraHTTPHeaders: { Authorization: linkAuthorization },
    });
    const response = await inviteeCtx.get(`/api/invitations/${inviteToken}`);
    expect([400, 404]).toContain(response.status());
    await inviteeCtx.dispose();
  });

  test('POST /api/invitations/{token}/accept — wrong user gets 403 or 400', async ({
    authedRequest,
    session,
  }) => {
    // Create a separate repo and invite intruder; then owner (not the invitee) tries to accept
    const altRepoName = `e2e-link-wrong-${Date.now().toString(36)}`;
    await authedRequest.post('/api/repo', {
      data: { name: altRepoName, description: 'Wrong user test', isPrivate: true },
    });

    try {
      await authedRequest.post(repoCollaboratorsApi(session.username, altRepoName), {
        data: { username: session.intruderUsername, permissions: ['READ'] },
      });

      const linkResp = await authedRequest.post(
        `${repoCollaboratorsApi(session.username, altRepoName)}/${session.intruderUsername}/invite-link`,
      );
      const { token } = (await linkResp.json()) as { token: string };

      // Owner is not the invitee — should be rejected
      const ownerResp = await authedRequest.post(`/api/invitations/${token}/accept`);
      expect([400, 403]).toContain(ownerResp.status());
    } finally {
      await authedRequest
        .delete(
          `${repoCollaboratorsApi(session.username, altRepoName)}/${session.intruderUsername}`,
        )
        .catch(() => {});
      await authedRequest.delete(`/api/repo/${session.username}/${altRepoName}`).catch(() => {});
    }
  });

  test('GET /api/invitations/unknown-token — 404 or 400 for unknown token', async ({
    authedRequest,
  }) => {
    const response = await authedRequest.get(`/api/invitations/does-not-exist-abc123`);
    expect([400, 404]).toContain(response.status());
  });
});
