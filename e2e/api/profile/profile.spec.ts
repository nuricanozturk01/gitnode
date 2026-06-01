import { usersApi } from '@helpers/paths';

import { expect, test } from '../fixtures/authenticated-api';

test.describe.serial('Profile API — all endpoints', () => {
  test('GET /api/users/me', async ({ authedRequest, session }) => {
    const response = await authedRequest.get(`${usersApi}/me`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.username).toBe(session.username);
    expect(body.email).toBe(session.email);
  });

  test('PATCH /api/users/me/display-name', async ({ authedRequest }) => {
    const response = await authedRequest.patch(`${usersApi}/me/display-name`, {
      data: { displayName: 'E2E Display' },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.displayName).toBe('E2E Display');
  });

  test('PATCH /api/users/me/profile', async ({ authedRequest }) => {
    const response = await authedRequest.patch(`${usersApi}/me/profile`, {
      data: {
        bio: 'Profile patch',
        website: 'https://example.com',
        location: 'Test',
      },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.website).toBe('https://example.com');
  });

  test('PATCH /api/users/me/password', async ({ authedRequest, session }) => {
    const response = await authedRequest.patch(`${usersApi}/me/password`, {
      data: {
        currentPassword: session.password,
        newPassword: session.password,
      },
    });
    expect(response.status()).toBe(204);
  });

  test('GET /api/users/{username}', async ({ authedRequest, session }) => {
    const response = await authedRequest.get(`${usersApi}/${session.username}`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.username).toBe(session.username);
  });

  test('GET /api/users/search', async ({ authedRequest, session }) => {
    const response = await authedRequest.get(`${usersApi}/search`, {
      params: { q: session.username.slice(0, 4) },
    });
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  // PATCH /api/users/me with username is omitted: even unchanged username triggers
  // UsernameChangedEvent → renameBaseDir deletes the owner git directory (backend bug).
});
