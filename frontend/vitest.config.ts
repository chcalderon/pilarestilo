import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  resolve: {
    alias: {
      // Vitest does not read astro.config.mjs, so the `@/*` alias is declared here too.
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // Virtual module from Astro's Vite plugin, which Vitest never loads. See the stub file.
      'astro:middleware': fileURLToPath(new URL('./src/testing/astroMiddlewareStub.ts', import.meta.url)),
    },
  },
  test: {
    environment: 'happy-dom',
    globals: true,
    // e2e/ holds Playwright specs. Vitest's default glob picks them up and every one fails on
    // Playwright-only APIs (test.describe with a `page` fixture). They run via `npm run test:e2e`.
    exclude: ['node_modules/**', 'dist/**', 'e2e/**', 'test-results/**'],
    coverage: {
      // lcov for SonarQube, text for the terminal. Without a report Sonar shows 0% and its
      // quality gate on new code passes for the wrong reason.
      provider: 'v8',
      reporter: ['text-summary', 'lcov'],
      reportsDirectory: 'coverage',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.d.ts', 'src/**/__tests__/**'],
    },
  },
});
