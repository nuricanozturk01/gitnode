import { test as base, type APIRequestContext } from '@playwright/test';

import { getApiBaseUrl } from '../helpers/env';
import { createScenarioRepo, registerScenarioUser } from '../helpers/scenario-api';
import type { ScenarioRepo, ScenarioUser } from '../helpers/types';

type ScenarioFixtures = {
  apiBaseUrl: string;
  bareApi: APIRequestContext;
  owner: ScenarioUser;
  intruder: ScenarioUser;
  privateRepo: ScenarioRepo;
  publicRepo: ScenarioRepo;
};

export const test = base.extend<ScenarioFixtures>({
  apiBaseUrl: async ({}, use) => {
    await use(getApiBaseUrl());
  },

  bareApi: async ({ playwright }, use) => {
    const ctx = await playwright.request.newContext({ baseURL: getApiBaseUrl() });
    await use(ctx);
    await ctx.dispose();
  },

  owner: async ({ bareApi }, use) => {
    const user = await registerScenarioUser(bareApi);
    await use(user);
  },

  intruder: async ({ bareApi }, use) => {
    const user = await registerScenarioUser(bareApi);
    await use(user);
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
