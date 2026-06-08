/**
 * SCN-SNIPPET-VISIBILITY — Snippet visibility and access control.
 *
 * Covers:
 *  SCN-SNIPPET-PUBLIC-ANON     — anonymous can read PUBLIC snippet (200)
 *  SCN-SNIPPET-PRIVATE-ANON    — anonymous cannot read PRIVATE snippet (404 — hidden as not found)
 *  SCN-SNIPPET-PRIVATE-OWNER   — owner can read their own PRIVATE snippet (200)
 *  SCN-SNIPPET-PRIVATE-INTRUDER — authenticated non-owner cannot read PRIVATE snippet (404)
 *  SCN-SNIPPET-PUBLIC-LIST     — public snippet appears in public list; private does not
 *  SCN-SNIPPET-OWNER-LIST      — owner sees own private snippets in /me list
 */
import type { APIRequestContext } from '@playwright/test';

import { expect, test } from './fixtures/scenario';
import { getApiBaseUrl } from './helpers/env';

const snippetsApi = '/api/snippets';

// ─── SCN-SNIPPET-PUBLIC-ANON ──────────────────────────────────────────────────

test.describe('SCN-SNIPPET-PUBLIC-ANON — anonymous reads public snippet', () => {
  let publicSnippetId: string;
  let anonRequest: APIRequestContext;

  test.beforeAll(async ({ playwright, bareApi, owner }) => {
    const resp = await bareApi.post(snippetsApi, {
      headers: { Authorization: owner.authorization },
      data: {
        title: 'SCN Public Snippet',
        description: 'scenario test',
        visibility: 'PUBLIC',
        files: [{ filename: 'hello.ts', content: 'export const hello = "world";\n' }],
      },
    });
    expect(resp.status()).toBe(201);
    publicSnippetId = ((await resp.json()) as { id: string }).id;

    anonRequest = await playwright.request.newContext({
      baseURL: getApiBaseUrl(),
      extraHTTPHeaders: { Accept: 'application/json' },
    });
  });

  test.afterAll(async ({ bareApi, owner }) => {
    await bareApi
      .delete(`${snippetsApi}/${publicSnippetId}`, {
        headers: { Authorization: owner.authorization },
      })
      .catch(() => {});
    await anonRequest?.dispose();
  });

  test('GET /api/snippets/{id} — 200 without auth for public snippet', async () => {
    const resp = await anonRequest.get(`${snippetsApi}/${publicSnippetId}`);
    expect(resp.status()).toBe(200);
    expect(((await resp.json()) as { id: string }).id).toBe(publicSnippetId);
  });
});

// ─── SCN-SNIPPET-PRIVATE-ANON ─────────────────────────────────────────────────

test.describe('SCN-SNIPPET-PRIVATE-ANON — anonymous cannot read private snippet', () => {
  let privateSnippetId: string;
  let anonRequest: APIRequestContext;

  test.beforeAll(async ({ playwright, bareApi, owner }) => {
    const resp = await bareApi.post(snippetsApi, {
      headers: { Authorization: owner.authorization },
      data: {
        title: 'SCN Private Snippet',
        description: 'scenario test',
        visibility: 'PRIVATE',
        files: [{ filename: 'secret.ts', content: 'export const secret = 42;\n' }],
      },
    });
    expect(resp.status()).toBe(201);
    privateSnippetId = ((await resp.json()) as { id: string }).id;

    anonRequest = await playwright.request.newContext({
      baseURL: getApiBaseUrl(),
      extraHTTPHeaders: { Accept: 'application/json' },
    });
  });

  test.afterAll(async ({ bareApi, owner }) => {
    await bareApi
      .delete(`${snippetsApi}/${privateSnippetId}`, {
        headers: { Authorization: owner.authorization },
      })
      .catch(() => {});
    await anonRequest?.dispose();
  });

  test('GET /api/snippets/{id} — 404 without auth for private snippet', async () => {
    const resp = await anonRequest.get(`${snippetsApi}/${privateSnippetId}`);
    expect(resp.status()).toBe(404);
  });
});

// ─── SCN-SNIPPET-PRIVATE-OWNER ────────────────────────────────────────────────

test.describe('SCN-SNIPPET-PRIVATE-OWNER — owner can read own private snippet', () => {
  let privateSnippetId: string;

  test.beforeAll(async ({ bareApi, owner }) => {
    const resp = await bareApi.post(snippetsApi, {
      headers: { Authorization: owner.authorization },
      data: {
        title: 'SCN Owner Private Snippet',
        description: 'owner only',
        visibility: 'PRIVATE',
        files: [{ filename: 'owner.ts', content: 'const owner = true;\n' }],
      },
    });
    expect(resp.status()).toBe(201);
    privateSnippetId = ((await resp.json()) as { id: string }).id;
  });

  test.afterAll(async ({ bareApi, owner }) => {
    await bareApi
      .delete(`${snippetsApi}/${privateSnippetId}`, {
        headers: { Authorization: owner.authorization },
      })
      .catch(() => {});
  });

  test('GET /api/snippets/{id} — 200 for owner accessing own private snippet', async ({
    bareApi,
    owner,
  }) => {
    const resp = await bareApi.get(`${snippetsApi}/${privateSnippetId}`, {
      headers: { Authorization: owner.authorization },
    });
    expect(resp.status()).toBe(200);
    expect(((await resp.json()) as { id: string }).id).toBe(privateSnippetId);
  });
});

// ─── SCN-SNIPPET-PRIVATE-INTRUDER ─────────────────────────────────────────────

test.describe('SCN-SNIPPET-PRIVATE-INTRUDER — authenticated non-owner gets 404', () => {
  let privateSnippetId: string;

  test.beforeAll(async ({ bareApi, owner }) => {
    const resp = await bareApi.post(snippetsApi, {
      headers: { Authorization: owner.authorization },
      data: {
        title: 'SCN Intruder Target',
        description: 'intruder test',
        visibility: 'PRIVATE',
        files: [{ filename: 'hidden.ts', content: 'const hidden = true;\n' }],
      },
    });
    expect(resp.status()).toBe(201);
    privateSnippetId = ((await resp.json()) as { id: string }).id;
  });

  test.afterAll(async ({ bareApi, owner }) => {
    await bareApi
      .delete(`${snippetsApi}/${privateSnippetId}`, {
        headers: { Authorization: owner.authorization },
      })
      .catch(() => {});
  });

  test('GET /api/snippets/{id} — 404 for authenticated non-owner on private snippet', async ({
    bareApi,
    intruder,
  }) => {
    const resp = await bareApi.get(`${snippetsApi}/${privateSnippetId}`, {
      headers: { Authorization: intruder.authorization },
    });
    expect(resp.status()).toBe(404);
  });
});

// ─── SCN-SNIPPET-PUBLIC-LIST ──────────────────────────────────────────────────

test.describe('SCN-SNIPPET-PUBLIC-LIST — public/private snippet list separation', () => {
  let publicSnippetId: string;
  let privateSnippetId: string;

  test.beforeAll(async ({ bareApi, owner }) => {
    const pub = await bareApi.post(snippetsApi, {
      headers: { Authorization: owner.authorization },
      data: {
        title: 'SCN List Public',
        description: '',
        visibility: 'PUBLIC',
        files: [{ filename: 'pub.ts', content: 'export const pub = 1;\n' }],
      },
    });
    publicSnippetId = ((await pub.json()) as { id: string }).id;

    const priv = await bareApi.post(snippetsApi, {
      headers: { Authorization: owner.authorization },
      data: {
        title: 'SCN List Private',
        description: '',
        visibility: 'PRIVATE',
        files: [{ filename: 'priv.ts', content: 'const priv = 1;\n' }],
      },
    });
    privateSnippetId = ((await priv.json()) as { id: string }).id;
  });

  test.afterAll(async ({ bareApi, owner }) => {
    for (const id of [publicSnippetId, privateSnippetId]) {
      await bareApi
        .delete(`${snippetsApi}/${id}`, { headers: { Authorization: owner.authorization } })
        .catch(() => {});
    }
  });

  test('public snippet appears in /api/snippets list (no auth)', async ({ bareApi }) => {
    const resp = await bareApi.get(snippetsApi, { params: { page: '0', size: '100' } });
    expect(resp.status()).toBe(200);
    const body = (await resp.json()) as { content: { id: string }[] };
    expect(body.content.some((s) => s.id === publicSnippetId)).toBe(true);
    expect(body.content.some((s) => s.id === privateSnippetId)).toBe(false);
  });

  test('owner sees private snippet in /api/snippets/me', async ({ bareApi, owner }) => {
    const resp = await bareApi.get(`${snippetsApi}/me`, {
      headers: { Authorization: owner.authorization },
    });
    expect(resp.status()).toBe(200);
    const body = (await resp.json()) as { content: { id: string }[] };
    expect(body.content.some((s) => s.id === privateSnippetId)).toBe(true);
  });
});
