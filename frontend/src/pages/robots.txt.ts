import type { APIRoute } from 'astro';
import { resolveSiteUrl } from '../lib/siteUrl';

export const prerender = false;

/** Paths with nothing to index: the panel, and the per-locale account / checkout / auth flows. */
const DISALLOW = [
  '/admin/',
  '/*/checkout',
  '/*/cart',
  '/*/account',
  '/*/auth/',
  '/*/wishlist',
  '/api/',
];

export const GET: APIRoute = ({ request }) => {
  const site = resolveSiteUrl(new URL(request.url), request.headers);
  const body = [
    'User-agent: *',
    ...DISALLOW.map((path) => `Disallow: ${path}`),
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
