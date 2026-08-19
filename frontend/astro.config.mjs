import { fileURLToPath } from 'node:url';
import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import node from '@astrojs/node';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  output: 'server',
  adapter: node({ mode: 'standalone' }),
  integrations: [react()],
  vite: {
    // Astro 5 dropped @astrojs/tailwind; Tailwind ships as a Vite plugin now.
    plugins: [tailwindcss()],
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
