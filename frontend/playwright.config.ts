import { defineConfig, devices } from '@playwright/test';

// Set TEST_BASE_URL to run against an already-running stack (e.g. the Docker compose stack behind
// Caddy at https://localhost) instead of booting a dev server. Caddy serves a self-signed cert
// there, hence ignoreHTTPSErrors.
const externalBaseUrl = process.env.TEST_BASE_URL;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: 'html',
  use: {
    baseURL: externalBaseUrl ?? 'http://localhost:4321',
    trace: 'on-first-retry',
    ignoreHTTPSErrors: true,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: externalBaseUrl
    ? undefined
    : {
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
