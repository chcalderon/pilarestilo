import { fileURLToPath } from 'node:url';
import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import tailwind from '@astrojs/tailwind';
import node from '@astrojs/node';

export default defineConfig({
  output: 'server',
  adapter: node({ mode: 'standalone' }),
  integrations: [react(), tailwind()],
  vite: {
    resolve: {
      // Astro derives the `@/*` alias from tsconfig `baseUrl`, which TypeScript deprecated and
      // drops in 7.0. Declaring the alias here lets tsconfig keep `paths` without `baseUrl`.
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
  },
  i18n: {
    locales: ['es', 'en'],
    defaultLocale: 'es',
    routing: 'manual'
  }
});
