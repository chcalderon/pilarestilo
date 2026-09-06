import type { APIRoute } from 'astro';
import { resolveSiteUrl } from '../lib/siteUrl';

export const prerender = false;

/** Paths with nothing to index: the panel, and the per-locale account / checkout / auth flows. */
const PRIVATE_PATHS = [
  '/admin/',
  '/*/checkout',
  '/*/cart',
  '/*/account',
  '/*/auth/',
  '/*/wishlist',
];

/**
 * `Googlebot` and `Googlebot-Image` get their own groups so they can reach `/api/media/` —
 * product images live there and Merchant Center's quality checks need them. Everything else under
 * `/api/` is JSON with no `<head>` and no canonical; leaving it open had Google crawling
 * `/api/products`, `/api/categories`, every paginated variant, and reporting them all as
 * "Duplicada: sin versión canónica". `Allow` beats `Disallow` on the longest match for Google, so
 * `/api/media/x.jpg` stays crawlable while `/api/products` does not. Merchant Center also looks
 * for these two user-agents by name.
 */
export const GET: APIRoute = ({ request }) => {
  const site = resolveSiteUrl(new URL(request.url), request.headers);

  const body = [
    'User-agent: Googlebot',
    ...PRIVATE_PATHS.map((path) => `Disallow: ${path}`),
    'Disallow: /api/',
    'Allow: /api/media/',
    '',
    'User-agent: Googlebot-Image',
    'Disallow: /admin/',
    'Disallow: /api/',
    'Allow: /api/media/',
    '',
    'User-agent: *',
    ...PRIVATE_PATHS.map((path) => `Disallow: ${path}`),
    'Disallow: /api/',
    'Disallow: /phog/',
    '',
    `Sitemap: ${site}/sitemap.xml`,
    '',
  ].join('\n');

  return new Response(body, {
    headers: {
      'Content-Type': 'text/plain; charset=utf-8',
      'Cache-Control': 'public, max-age=86400',
    },
  });
};
