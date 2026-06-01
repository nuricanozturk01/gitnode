import { ApiClient } from '@helpers/api-client';
import type { E2eSession } from '@helpers/types';
import { type APIRequestContext, test as base } from '@playwright/test';

import { loadValidSession } from '../../helpers/session-auth';
import { getApiBaseUrl } from '../helpers/env';

interface AuthenticatedFixtures {
  session: E2eSession;
  authedRequest: APIRequestContext;
  api: ApiClient;
}

export const test = base.extend<AuthenticatedFixtures>({
  session: [
    async ({ playwright }, use) => {
      const request = await playwright.request.newContext({ baseURL: getApiBaseUrl() });
      try {
        await use(await loadValidSession(request));
      } finally {
        await request.dispose();
      }
    },
    { scope: 'worker' },
  ],

  authedRequest: async ({ playwright, session }, use) => {
    const context = await playwright.request.newContext({
      baseURL: session.baseUrl,
      extraHTTPHeaders: {
        Authorization: session.authorization,
        Accept: 'application/json',
      },
    });
    await use(context);
    await context.dispose();
  },

  api: async ({ authedRequest, session }, use) => {
    await use(new ApiClient(authedRequest, session));
  },
});

export { expect } from '@playwright/test';
