import type { APIRoute } from 'astro';
import { getProducts } from '../lib/api';
import { resolveSiteUrl } from '../lib/siteUrl';
import { merchantFeedItems, merchantFeedXml } from '../lib/merchantFeed';

export const prerender = false;

const PAGE_SIZE = 200;
const MAX_PAGES = 25; // 5,000 products before the feed needs splitting

/**
 * Google Merchant Center product feed. Add this URL as a primary data source in Merchant Center
 * (country: Chile, language: es) and disable "found by Google" — that crawler mis-targeted the
 * store to Spain and guessed the currency.
 */
export const GET: APIRoute = async ({ request }) => {
  const site = resolveSiteUrl(new URL(request.url), request.headers);

  const items: string[] = [];
  for (let page = 0; page < MAX_PAGES; page += 1) {
    const result = await getProducts({ page, size: PAGE_SIZE, active: true });
    for (const product of result.content) {
      if (product.active === false) continue;
      items.push(...merchantFeedItems(product, { siteUrl: site, headers: request.headers }));
    }
    if (result.number >= result.totalPages - 1 || result.content.length === 0) break;
  }

  const xml = merchantFeedXml(items, { siteUrl: site });

  return new Response(xml, {
    headers: {
      'Content-Type': 'application/xml; charset=utf-8',
      'Cache-Control': 'public, max-age=1800',
    },
  });
};
