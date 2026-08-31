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
 * `Googlebot` and `Googlebot-Image` get their own groups so they are NOT subject to the
 * `Disallow: /api/` that applies to everyone else — product images are served under
 * `/api/media/`, and Merchant Center's quality checks need to reach both the product pages and
 * their images. Merchant Center also specifically looks for these two user-agents by name.
 */
export const GET: APIRoute = ({ request }) => {
  const site = resolveSiteUrl(new URL(request.url), request.headers);

  const body = [
    'User-agent: Googlebot',
    ...PRIVATE_PATHS.map((path) => `Disallow: ${path}`),
    '',
    'User-agent: Googlebot-Image',
    'Disallow: /admin/',
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
