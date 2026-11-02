import { getApiBaseUrl } from '@helpers/env';
import { expect, request as playwrightRequest, test } from '@playwright/test';

async function register(ctx: Awaited<ReturnType<typeof playwrightRequest.newContext>>) {
  const username = `rot_${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
  const response = await ctx.post('/api/auth/register', { data: { username, email: `${username}@example.org`, password: 'Passw0rd!x1' } });
  expect(response.status()).toBe(200);
  return (await response.json()) as { token: string; refreshToken: string };
}

test('refresh tokens are single-use, replay revokes the login, logout revokes it', async () => {
  test.setTimeout(60_000);
  const ctx = await playwrightRequest.newContext({ baseURL: getApiBaseUrl() });
  try {
    const first = await register(ctx);
    const refresh = (token: string) => ctx.post('/api/auth/refresh-token', { data: { refreshToken: token } });

    const rotated = await refresh(first.refreshToken);
    expect(rotated.status()).toBe(200);
    const second = (await rotated.json()) as { refreshToken: string };
    expect(second.refreshToken).not.toBe(first.refreshToken);

    // Immediate replay (two tabs racing) is rejected but does not kill the session.
    expect((await refresh(first.refreshToken)).status()).toBe(401);
    const third = await refresh(second.refreshToken);
    expect(third.status()).toBe(200);
    const thirdToken = ((await third.json()) as { refreshToken: string }).refreshToken;

    // Replay long after use means theft: the whole family is revoked.
    await new Promise((resolve) => setTimeout(resolve, 16_000));
    expect((await refresh(first.refreshToken)).status()).toBe(401);
    expect((await refresh(thirdToken)).status()).toBe(401);

    const other = await register(ctx);
    expect((await ctx.post('/api/auth/logout', { data: { refreshToken: other.refreshToken } })).status()).toBe(204);
    expect((await refresh(other.refreshToken)).status()).toBe(401);
    expect((await ctx.post('/api/auth/logout', { data: { refreshToken: 'garbage' } })).status()).toBe(204);
  } finally {
    await ctx.dispose();
  }
});
