import './helpers/load-env';

import { defineConfig } from '@playwright/test';

const apiBaseURL = process.env.ORIGINHUB_API_BASE_URL ?? 'http://localhost:8080';
/** Set when running `pnpm test:e2e:scenario` so scenario does not wait for the api project. */
const scenarioOnly = process.env.E2E_SCENARIO_ONLY === '1';
/** Set when running `pnpm test:e2e:teardown` to delete users without re-running scenario. */
const teardownOnly = process.env.E2E_TEARDOWN_ONLY === '1';

export default defineConfig({
  globalSetup: require.resolve('./global-setup'),
  globalTeardown: require.resolve('./global-teardown'),
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  projects: [
    {
      name: 'api',
      testDir: './api',
      testMatch: '**/*.spec.ts',
      fullyParallel: false,
      workers: 1,
      use: {
        baseURL: apiBaseURL,
        trace: 'on-first-retry',
      },
    },
    {
      name: 'scenario',
      testDir: './scenario',
      testMatch: '**/*.spec.ts',
      dependencies: scenarioOnly ? [] : ['api'],
      fullyParallel: true,
      workers: process.env.CI ? 2 : 4,
      use: {
        baseURL: apiBaseURL,
      },
    },
    {
      name: 'teardown',
      testDir: './teardown',
      testMatch: '**/*.spec.ts',
      dependencies: teardownOnly ? [] : ['scenario'],
      fullyParallel: false,
      workers: 1,
      use: {
        baseURL: apiBaseURL,
      },
    },
  ],
});
