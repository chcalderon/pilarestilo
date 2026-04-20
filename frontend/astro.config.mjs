import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import tailwind from '@astrojs/tailwind';
import node from '@astrojs/node';

export default defineConfig({
  output: 'server',
  adapter: node({ mode: 'standalone' }),
  integrations: [react(), tailwind()],
  i18n: {
    locales: ['es', 'en'],
    defaultLocale: 'es',
    routing: 'manual'
  }
});
