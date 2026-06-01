import { defineConfig } from '@playwright/test';

const baseURL = process.env.ORIGINHUB_API_BASE_URL ?? 'http://localhost:8080';

export default defineConfig({
  globalSetup: require.resolve('./api/global-setup'),
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'api',
      testDir: './api',
      testMatch: '**/*.spec.ts',
    },
    {
      name: 'scenario',
      testDir: './scenario',
      testMatch: '**/*.spec.ts',
    },
  ],
});
