/**
 * Deletes shared E2E users created in global-setup via DELETE /api/users/me.
 * Runs last (teardown project depends on scenario). Intentionally not in api/profile tests
 * because api tests need the session user for the full run.
 */
import fs from 'node:fs';

import { loadSession, SESSION_FILE } from '@helpers/auth-store';
import { getApiBaseUrl } from '@helpers/env';
import { usersApi } from '@helpers/paths';
import { expect, request, test } from '@playwright/test';

import { cleanupE2eTempDirs } from '../helpers/cleanup';

test.describe.serial('E2E teardown', () => {
  test('DELETE /api/users/me for intruder then owner', async () => {
    const session = loadSession();

    if (session.preserveUsers) {
      test.skip(true, 'preserveUsers: accounts came from .env — not deleted');
    }

    const baseURL = getApiBaseUrl();

    const intruderCtx = await request.newContext({
      baseURL,
      extraHTTPHeaders: {
        Authorization: session.intruderAuthorization,
        Accept: 'application/json',
      },
    });
    try {
      const response = await intruderCtx.delete(`${usersApi}/me`);
      expect(response.status()).toBe(204);
    } finally {
      await intruderCtx.dispose();
    }

    const ownerCtx = await request.newContext({
      baseURL,
      extraHTTPHeaders: {
        Authorization: session.authorization,
        Accept: 'application/json',
      },
    });
    try {
      const response = await ownerCtx.delete(`${usersApi}/me`);
      expect(response.status()).toBe(204);
    } finally {
      await ownerCtx.dispose();
    }

    if (fs.existsSync(SESSION_FILE)) {
      fs.unlinkSync(SESSION_FILE);
    }
  });

  test('remove ephemeral git workdirs', () => {
    cleanupE2eTempDirs();
  });
});
