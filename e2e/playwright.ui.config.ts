import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './ui',
  use: {
    baseURL: process.env.GITNODE_FRONTEND_BASE_URL ?? 'http://localhost:4200',
    launchOptions: process.env.GITNODE_BROWSER_EXECUTABLE
      ? { executablePath: process.env.GITNODE_BROWSER_EXECUTABLE }
      : {},
  },
});
