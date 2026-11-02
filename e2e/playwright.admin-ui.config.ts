import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './admin-ui',
  use: {
    baseURL: process.env.GITNODE_ADMIN_BASE_URL ?? 'http://127.0.0.1:4300',
    launchOptions: process.env.GITNODE_BROWSER_EXECUTABLE
      ? { executablePath: process.env.GITNODE_BROWSER_EXECUTABLE }
      : {},
  },
});
