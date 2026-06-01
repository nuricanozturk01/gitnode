import { loadSession } from '@helpers/auth-store';
import { type APIRequestContext, test as base } from '@playwright/test';

import { getApiBaseUrl } from '../helpers/env';
import { createScenarioRepo, sessionIntruder, sessionOwner } from '../helpers/scenario-api';
import type { ScenarioRepo, ScenarioUser } from '../helpers/types';

interface ScenarioFixtures {
  apiBaseUrl: string;
  bareApi: APIRequestContext;
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

  owner: async ({}, use) => {
    await use(sessionOwner(loadSession()));
  },

  intruder: async ({}, use) => {
    await use(sessionIntruder(loadSession()));
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
