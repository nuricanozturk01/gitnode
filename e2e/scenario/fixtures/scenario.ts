import type { E2eSession } from '@helpers/types';
import { type APIRequestContext, test as base } from '@playwright/test';

import { loadValidSession } from '../../helpers/session-auth';
import { getApiBaseUrl } from '../helpers/env';
import { createScenarioRepo, sessionIntruder, sessionOwner } from '../helpers/scenario-api';
import type { ScenarioRepo, ScenarioUser } from '../helpers/types';

interface ScenarioFixtures {
  apiBaseUrl: string;
  bareApi: APIRequestContext;
  session: E2eSession;
  owner: ScenarioUser;
  intruder: ScenarioUser;
  privateRepo: ScenarioRepo;
  publicRepo: ScenarioRepo;
}

export const test = base.extend<ScenarioFixtures>({
  apiBaseUrl: async ({}, use) => {
    await use(getApiBaseUrl());
  },

  bareApi: async ({ playwright }, use) => {
    const ctx = await playwright.request.newContext({ baseURL: getApiBaseUrl() });
    await use(ctx);
    await ctx.dispose();
  },

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

  owner: async ({ session }, use) => {
    await use(sessionOwner(session));
  },

  intruder: async ({ session }, use) => {
    await use(sessionIntruder(session));
  },

  privateRepo: async ({ bareApi, owner }, use) => {
    const repo = await createScenarioRepo(bareApi, owner, {
      isPrivate: true,
      namePrefix: 'scn-priv',
    });
    await use(repo);
  },

  publicRepo: async ({ bareApi, owner }, use) => {
    const repo = await createScenarioRepo(bareApi, owner, {
      isPrivate: false,
      namePrefix: 'scn-pub',
    });
    await use(repo);
  },
});

export { expect } from '@playwright/test';
