import { ApiClient } from '@helpers/api-client';
import { loadSession } from '@helpers/auth-store';
import type { E2eSession } from '@helpers/types';
import { type APIRequestContext, test as base } from '@playwright/test';

interface AuthenticatedFixtures {
  session: E2eSession;
  authedRequest: APIRequestContext;
  api: ApiClient;
}

export const test = base.extend<AuthenticatedFixtures>({
  session: async ({}, use) => {
    await use(loadSession());
  },

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
