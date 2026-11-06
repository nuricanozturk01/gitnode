import { getApiBaseUrl } from '@helpers/env';
import { authApi, repoApi } from '@helpers/paths';
import { expect, request as playwrightRequest, test } from '@playwright/test';

/**
 * Locks the shape of error bodies: RFC 7807 problem+json with the machine code in `detail`
 * and (for clients that predate problem bodies) in `message`, plus a `traceId` that matches
 * the `X-Request-Id` response header.
 */
test.describe('Error body contract', () => {
  test('not found carries status, code, message and a trace id', async () => {
    const ctx = await playwrightRequest.newContext({ baseURL: getApiBaseUrl() });
    try {
      const resp = await ctx.get(`${repoApi}/nobody-${Date.now().toString(36)}/none`);
      expect(resp.status()).toBe(404);
      expect(resp.headers()['content-type']).toContain('application/problem+json');
      const body = (await resp.json()) as Record<string, unknown>;
      expect(body.status).toBe(404);
      expect(typeof body.detail).toBe('string');
      expect(body.message).toBe(body.detail);
      expect(body.traceId).toBe(resp.headers()['x-request-id']);
    } finally {
      await ctx.dispose();
    }
  });

  test('a sane X-Request-Id from the caller is echoed, junk is replaced', async () => {
    const ctx = await playwrightRequest.newContext({ baseURL: getApiBaseUrl() });
    try {
      const mine = 'e2e-request-id-12345';
      const echoed = await ctx.get(`${repoApi}/nobody/none`, { headers: { 'X-Request-Id': mine } });
      expect(echoed.headers()['x-request-id']).toBe(mine);
      expect(((await echoed.json()) as { traceId: string }).traceId).toBe(mine);

      const junk = await ctx.get(`${repoApi}/nobody/none`, {
        headers: { 'X-Request-Id': 'a b\tc' },
      });
      expect(junk.headers()['x-request-id']).not.toBe('a b\tc');
    } finally {
      await ctx.dispose();
    }
  });

  test('bad credentials are rejected with a problem body', async () => {
    const ctx = await playwrightRequest.newContext({ baseURL: getApiBaseUrl() });
    try {
      const resp = await ctx.post(`${authApi}/login`, {
        data: { usernameOrEmail: 'nobody-at-all', password: 'wrong-Password1!' },
      });
      expect(resp.status()).toBeGreaterThanOrEqual(400);
      expect(resp.status()).toBeLessThan(500);
      const body = (await resp.json()) as Record<string, unknown>;
      expect(body.status).toBe(resp.status());
      expect(typeof body.message).toBe('string');
    } finally {
      await ctx.dispose();
    }
  });
});
