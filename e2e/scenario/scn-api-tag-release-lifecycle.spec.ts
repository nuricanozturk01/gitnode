/**
 * SCN-TAG-RELEASE — Tag and release lifecycle.
 *
 * Covers:
 *  SCN-TAG-CREATE-DELETE   — owner creates tag on HEAD, verifies it appears, then deletes it
 *  SCN-RELEASE-LIFECYCLE   — owner creates release tied to tag, patches it, deletes it
 *  SCN-TAG-PUBLIC-READ     — unauthenticated can read tags/releases on public repo
 *  SCN-TAG-PRIVATE-BLOCKED — unauthenticated cannot read tags/releases on private repo (403)
 */
import type { APIRequestContext } from '@playwright/test';

import { expect, test } from './fixtures/scenario';
import { getApiBaseUrl } from './helpers/env';

function tagsApi(owner: string, repo: string): string {
  return `/api/repos/${owner}/${repo}/tags`;
}

function releasesApi(owner: string, repo: string): string {
  return `/api/repos/${owner}/${repo}/releases`;
}

async function getHeadSha(
  bareApi: APIRequestContext,
  authorization: string,
  owner: string,
  repo: string,
): Promise<string> {
  const resp = await bareApi.get(`/api/repos/${owner}/${repo}/commits`, {
    headers: { Authorization: authorization },
    params: { branch: 'main', page: '0', size: '1' },
  });
  if (!resp.ok()) throw new Error(`get commits failed (${resp.status()})`);
  const body = (await resp.json()) as { items: { sha: string }[] };
  return body.items[0].sha;
}

// ─── SCN-TAG-CREATE-DELETE ────────────────────────────────────────────────────

test.describe('SCN-TAG-CREATE-DELETE — tag lifecycle', () => {
  test('owner creates tag on HEAD, verifies it, deletes it', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const tagName = `v1.0-scn-${Date.now().toString(36)}`;
    const sha = await getHeadSha(bareApi, owner.authorization, owner.username, privateRepo.name);

    // Create tag
    const createResp = await bareApi.post(tagsApi(owner.username, privateRepo.name), {
      headers: { Authorization: owner.authorization },
      data: { name: tagName, sha, message: 'SCN tag' },
    });
    expect(createResp.status()).toBe(201);

    // Tag appears in list
    const listResp = await bareApi.get(tagsApi(owner.username, privateRepo.name), {
      headers: { Authorization: owner.authorization },
    });
    expect(listResp.status()).toBe(200);
    const tags = (await listResp.json()) as { name: string }[];
    expect(tags.some((t) => t.name === tagName)).toBe(true);

    // Get single tag
    const getResp = await bareApi.get(`${tagsApi(owner.username, privateRepo.name)}/${tagName}`, {
      headers: { Authorization: owner.authorization },
    });
    expect(getResp.status()).toBe(200);
    expect(((await getResp.json()) as { name: string }).name).toBe(tagName);

    // Delete tag
    const deleteResp = await bareApi.delete(
      `${tagsApi(owner.username, privateRepo.name)}/${tagName}`,
      { headers: { Authorization: owner.authorization } },
    );
    expect(deleteResp.status()).toBe(204);

    // Tag gone after delete
    const listAfter = await bareApi.get(tagsApi(owner.username, privateRepo.name), {
      headers: { Authorization: owner.authorization },
    });
    const tagsAfter = (await listAfter.json()) as { name: string }[];
    expect(tagsAfter.some((t) => t.name === tagName)).toBe(false);
  });
});

// ─── SCN-RELEASE-LIFECYCLE ────────────────────────────────────────────────────

test.describe('SCN-RELEASE-LIFECYCLE — release lifecycle', () => {
  test('owner creates release tied to tag, patches it, deletes it, then deletes tag', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const tagName = `v2.0-scn-${Date.now().toString(36)}`;
    const sha = await getHeadSha(bareApi, owner.authorization, owner.username, privateRepo.name);

    // Create tag first
    await bareApi.post(tagsApi(owner.username, privateRepo.name), {
      headers: { Authorization: owner.authorization },
      data: { name: tagName, sha, message: 'Release tag' },
    });

    // Create release tied to existing tag
    const createResp = await bareApi.post(releasesApi(owner.username, privateRepo.name), {
      headers: { Authorization: owner.authorization },
      data: {
        tagName,
        name: 'SCN Release',
        body: 'Scenario release notes',
        isDraft: false,
        isPrerelease: false,
        createNewTag: false,
      },
    });
    expect(createResp.status()).toBe(201);
    const release = (await createResp.json()) as { id: string; name: string };
    expect(release.name).toBe('SCN Release');
    const releaseId = release.id;

    // List releases — release appears
    const listResp = await bareApi.get(releasesApi(owner.username, privateRepo.name), {
      headers: { Authorization: owner.authorization },
    });
    expect(listResp.status()).toBe(200);
    const releases = (await listResp.json()) as { id: string }[];
    expect(releases.some((r) => r.id === releaseId)).toBe(true);

    // Get by tag name
    const byTagResp = await bareApi.get(
      `${releasesApi(owner.username, privateRepo.name)}/tag/${tagName}`,
      { headers: { Authorization: owner.authorization } },
    );
    expect(byTagResp.status()).toBe(200);
    expect(((await byTagResp.json()) as { id: string }).id).toBe(releaseId);

    // Patch release
    const patchResp = await bareApi.patch(
      `${releasesApi(owner.username, privateRepo.name)}/${releaseId}`,
      {
        headers: { Authorization: owner.authorization },
        data: { name: 'SCN Release Updated' },
      },
    );
    expect(patchResp.status()).toBe(200);
    expect(((await patchResp.json()) as { name: string }).name).toBe('SCN Release Updated');

    // Delete release
    const deleteReleaseResp = await bareApi.delete(
      `${releasesApi(owner.username, privateRepo.name)}/${releaseId}`,
      { headers: { Authorization: owner.authorization } },
    );
    expect(deleteReleaseResp.status()).toBe(204);

    // Delete tag
    await bareApi.delete(`${tagsApi(owner.username, privateRepo.name)}/${tagName}`, {
      headers: { Authorization: owner.authorization },
    });
  });
});

// ─── SCN-TAG-PUBLIC-READ ──────────────────────────────────────────────────────

test.describe('SCN-TAG-PUBLIC-READ — unauthenticated read on public repo', () => {
  let anonRequest: APIRequestContext;

  test.beforeAll(async ({ playwright, bareApi, owner, publicRepo }) => {
    const tagName = `v0.1-scn-${Date.now().toString(36)}`;
    const sha = await getHeadSha(bareApi, owner.authorization, owner.username, publicRepo.name);
    await bareApi.post(tagsApi(owner.username, publicRepo.name), {
      headers: { Authorization: owner.authorization },
      data: { name: tagName, sha, message: 'Public tag' },
    });

    anonRequest = await playwright.request.newContext({
      baseURL: getApiBaseUrl(),
      extraHTTPHeaders: { Accept: 'application/json' },
    });
  });

  test.afterAll(async () => {
    await anonRequest?.dispose();
  });

  test('GET tags — 200 without auth on public repo', async ({ owner, publicRepo }) => {
    const resp = await anonRequest.get(tagsApi(owner.username, publicRepo.name));
    expect(resp.status()).toBe(200);
    expect(Array.isArray(await resp.json())).toBe(true);
  });

  test('GET releases — 200 without auth on public repo', async ({ owner, publicRepo }) => {
    const resp = await anonRequest.get(releasesApi(owner.username, publicRepo.name));
    expect(resp.status()).toBe(200);
    expect(Array.isArray(await resp.json())).toBe(true);
  });
});

// ─── SCN-TAG-PRIVATE-BLOCKED ──────────────────────────────────────────────────

test.describe('SCN-TAG-PRIVATE-BLOCKED — unauthenticated blocked on private repo', () => {
  let anonRequest: APIRequestContext;

  test.beforeAll(async ({ playwright }) => {
    anonRequest = await playwright.request.newContext({
      baseURL: getApiBaseUrl(),
      extraHTTPHeaders: { Accept: 'application/json' },
    });
  });

  test.afterAll(async () => {
    await anonRequest?.dispose();
  });

  test('GET tags — 403 without auth on private repo', async ({ owner, privateRepo }) => {
    const resp = await anonRequest.get(tagsApi(owner.username, privateRepo.name));
    expect(resp.status()).toBe(403);
  });

  test('GET releases — 403 without auth on private repo', async ({ owner, privateRepo }) => {
    const resp = await anonRequest.get(releasesApi(owner.username, privateRepo.name));
    expect(resp.status()).toBe(403);
  });
});
