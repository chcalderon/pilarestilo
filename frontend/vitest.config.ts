import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  resolve: {
    // Vitest does not read astro.config.mjs, so the `@/*` alias is declared here too.
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'happy-dom',
    globals: true,
    // e2e/ holds Playwright specs. Vitest's default glob picks them up and every one fails on
    // Playwright-only APIs (test.describe with a `page` fixture). They run via `npm run test:e2e`.
    exclude: ['node_modules/**', 'dist/**', 'e2e/**', 'test-results/**'],
  },
});
