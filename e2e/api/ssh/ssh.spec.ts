import { expect, test } from '../fixtures/authenticated-api';
import { sshKeysApi } from '@helpers/paths';

/** Valid ed25519 public key for E2E only (not a real deployed key). */
const E2E_SSH_PUBLIC_KEY =
  'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGbPdY9L1rF8KZ3k0vH5q8xY2nV7mP4sT6uW1eR9oAa e2e@test.local';

test.describe.serial('SSH keys API — all endpoints', () => {
  let keyId: string;

  test('GET /api/user/ssh-keys', async ({ authedRequest }) => {
    const response = await authedRequest.get(sshKeysApi);
    expect(response.status()).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('POST /api/user/ssh-keys', async ({ authedRequest }) => {
    const response = await authedRequest.post(sshKeysApi, {
      data: { title: 'E2E key', publicKey: E2E_SSH_PUBLIC_KEY },
    });
    expect(response.status()).toBe(201);
    const body = await response.json();
    expect(body.id).toBeTruthy();
    keyId = body.id;
  });

  test('DELETE /api/user/ssh-keys/{keyId}', async ({ authedRequest }) => {
    const response = await authedRequest.delete(`${sshKeysApi}/${keyId}`);
    expect(response.status()).toBe(204);
  });
});
