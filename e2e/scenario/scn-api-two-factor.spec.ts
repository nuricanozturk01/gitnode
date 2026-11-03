import { createHmac } from 'node:crypto';
import { getApiBaseUrl } from '@helpers/env';
import { expect, request as playwrightRequest, test } from '@playwright/test';

const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

function base32Decode(input: string): Buffer {
  let bits = '';
  for (const char of input.replace(/=+$/, '')) bits += ALPHABET.indexOf(char).toString(2).padStart(5, '0');
  const bytes: number[] = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) bytes.push(parseInt(bits.slice(i, i + 8), 2));
  return Buffer.from(bytes);
}

function totp(secret: string, stepOffset = 0): string {
  const counter = Math.floor(Date.now() / 1000 / 30) + stepOffset;
  const buffer = Buffer.alloc(8);
  buffer.writeBigUInt64BE(BigInt(counter));
  const hash = createHmac('sha1', base32Decode(secret)).update(buffer).digest();
  const offset = hash[hash.length - 1] & 0x0f;
  const binary = ((hash[offset] & 0x7f) << 24) | (hash[offset + 1] << 16) | (hash[offset + 2] << 8) | hash[offset + 3];
  return String(binary % 1_000_000).padStart(6, '0');
}

test('two-factor: setup, enable, login challenge, replay protection, recovery codes, git HTTP block, disable', async () => {
  test.setTimeout(60_000);
  const ctx = await playwrightRequest.newContext({ baseURL: getApiBaseUrl() });
  const username = `tfa_${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
  const password = 'Passw0rd!x1';
  try {
    const registered = await ctx.post('/api/auth/register', { data: { username, email: `${username}@example.org`, password } });
    expect(registered.status()).toBe(200);
    const { token } = (await registered.json()) as { token: string };
    const auth = { Authorization: `Bearer ${token}` };
    const login = (otp?: string) => ctx.post('/api/auth/login', { data: otp === undefined ? { usernameOrEmail: username, password } : { usernameOrEmail: username, password, otp } });

    expect(await (await ctx.get('/api/users/me/2fa', { headers: auth })).json()).toEqual({ enabled: false, recoveryCodesLeft: 0 });
    expect((await login()).status()).toBe(200);

    const setup = (await (await ctx.post('/api/users/me/2fa/setup', { headers: auth })).json()) as { secret: string; otpAuthUri: string };
    expect(setup.otpAuthUri).toContain('otpauth://totp/');
    expect(setup.otpAuthUri).toContain(`secret=${setup.secret}`);
    expect((await login()).status()).toBe(200); // not active until enabled

    expect((await ctx.post('/api/users/me/2fa/enable', { headers: auth, data: { code: '000000' } })).status()).toBe(400);
    const enabled = await ctx.post('/api/users/me/2fa/enable', { headers: auth, data: { code: totp(setup.secret) } });
    expect(enabled.status()).toBe(200);
    const { recoveryCodes } = (await enabled.json()) as { recoveryCodes: string[] };
    expect(recoveryCodes).toHaveLength(10);

    // Password alone is no longer enough.
    const missing = await login();
    expect(missing.status()).toBe(403);
    expect(await missing.text()).toContain('twoFactorRequired');
    const wrong = await login('000000');
    expect(wrong.status()).toBe(403);
    expect(await wrong.text()).toContain('invalidTwoFactorCode');

    // A fresh code works once; replaying the same step is rejected.
    const next = totp(setup.secret, 1);
    expect((await login(next)).status()).toBe(200);
    expect((await login(next)).status()).toBe(403);

    // Recovery codes are single use.
    expect((await login(recoveryCodes[0])).status()).toBe(200);
    expect((await login(recoveryCodes[0])).status()).toBe(403);
    expect((await (await ctx.get('/api/users/me/2fa', { headers: auth })).json()).recoveryCodesLeft).toBe(9);

    // HTTPS Git with the password is blocked (otherwise 2FA could be bypassed).
    const basic = `Basic ${Buffer.from(`${username}:${password}`).toString('base64')}`;
    const git = await ctx.get(`/git/${username}/none.git/info/refs?service=git-upload-pack`, { headers: { Authorization: basic } });
    expect(git.status()).toBe(401);
    expect(await git.text()).toContain('Two-factor');

    // Disable needs password and a valid code.
    expect((await ctx.post('/api/users/me/2fa/disable', { headers: auth, data: { password: 'Wrong123x', code: recoveryCodes[1] } })).status()).toBe(403);
    expect((await ctx.post('/api/users/me/2fa/disable', { headers: auth, data: { password, code: '000000' } })).status()).toBe(403);
    expect((await ctx.post('/api/users/me/2fa/disable', { headers: auth, data: { password, code: recoveryCodes[1] } })).status()).toBe(204);
    expect((await login()).status()).toBe(200);
    expect(await (await ctx.get('/api/users/me/2fa', { headers: auth })).json()).toEqual({ enabled: false, recoveryCodesLeft: 0 });
  } finally {
    const response = await ctx.post('/api/auth/login', { data: { usernameOrEmail: username, password } });
    if (response.ok()) {
      const { token } = (await response.json()) as { token: string };
      await ctx.delete('/api/users/me', { headers: { Authorization: `Bearer ${token}` } });
    }
    await ctx.dispose();
  }
});
