import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:4321',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:4321',
    reuseExistingServer: !process.env.CI,
    env: {
      ...process.env,
      INTERNAL_API_BASE_URL: process.env.INTERNAL_API_BASE_URL ?? 'http://127.0.0.1:8080/api',
      INTERNAL_API_TIMEOUT_MS: process.env.INTERNAL_API_TIMEOUT_MS ?? '1200',
    },
  },
});
