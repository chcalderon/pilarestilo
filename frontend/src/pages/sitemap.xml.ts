import type { APIRoute } from 'astro';
import { SUPPORTED_LOCALES } from '../i18n/index';
import { getProducts, getCategories } from '../lib/api';
import { resolveSiteUrl } from '../lib/siteUrl';

export const prerender = false;

/** Localised paths that exist for every locale and should be crawled. */
const STATIC_PATHS = [
  '',
  'products',
  'about',
  'how-we-sell',
  'contact',
  'shipping-returns',
  'privacy',
] as const;

const PAGE_SIZE = 200;
const MAX_PAGES = 25; // safety cap: 5,000 products before the sitemap needs splitting

interface UrlEntry {
  loc: string;
  lastmod?: string;
  changefreq?: string;
  priority?: string;
}

function xmlEscape(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/** A `<url>` block with an `xhtml:link` alternate per locale. */
function urlBlock(site: string, localisedPath: string, entry: Omit<UrlEntry, 'loc'>): string {
  const alternates = SUPPORTED_LOCALES.map(
    (loc) =>
      `    <xhtml:link rel="alternate" hreflang="${loc}" href="${xmlEscape(
        `${site}/${loc}${localisedPath ? `/${localisedPath}` : ''}`,
      )}"/>`,
  ).join('\n');

  const loc = `${site}/${SUPPORTED_LOCALES[0]}${localisedPath ? `/${localisedPath}` : ''}`;
  const lastmod = entry.lastmod ? `\n    <lastmod>${entry.lastmod}</lastmod>` : '';
  const changefreq = entry.changefreq ? `\n    <changefreq>${entry.changefreq}</changefreq>` : '';
  const priority = entry.priority ? `\n    <priority>${entry.priority}</priority>` : '';

  return `  <url>
    <loc>${xmlEscape(loc)}</loc>${lastmod}${changefreq}${priority}
${alternates}
  </url>`;
}

async function collectProductPaths(): Promise<Array<{ path: string; lastmod?: string }>> {
  const out: Array<{ path: string; lastmod?: string }> = [];
  for (let page = 0; page < MAX_PAGES; page += 1) {
    const result = await getProducts({ page, size: PAGE_SIZE, active: true });
    for (const product of result.content) {
      if (product.active === false) continue;
      out.push({
        path: `products/${product.id}`,
        lastmod: product.updatedAt ? product.updatedAt.slice(0, 10) : undefined,
      });
    }
    if (result.number >= result.totalPages - 1 || result.content.length === 0) break;
  }
  return out;
}

async function collectCategoryPaths(): Promise<string[]> {
  const categories = await getCategories();
  return categories
    .filter((c) => c.active !== false && !!c.slug)
    .map((c) => `categories/${c.slug}`);
}

export const GET: APIRoute = async ({ request }) => {
  const site = resolveSiteUrl(new URL(request.url), request.headers);

  const [productPaths, categoryPaths] = await Promise.all([
    collectProductPaths(),
    collectCategoryPaths(),
  ]);

  const staticPriority = (path: string): string => {
    if (path === '') return '1.0';
    if (path === 'products') return '0.9';
    return '0.5';
  };

  const blocks: string[] = [
    ...STATIC_PATHS.map((path) =>
      urlBlock(site, path, {
        changefreq: path === '' ? 'daily' : 'weekly',
        priority: staticPriority(path),
      }),
    ),
    ...categoryPaths.map((path) => urlBlock(site, path, { changefreq: 'weekly', priority: '0.7' })),
    ...productPaths.map(({ path, lastmod }) =>
      urlBlock(site, path, { lastmod, changefreq: 'weekly', priority: '0.8' }),
    ),
  ];

  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"
        xmlns:xhtml="http://www.w3.org/1999/xhtml">
${blocks.join('\n')}
</urlset>
`;

  return new Response(xml, {
    headers: {
      'Content-Type': 'application/xml; charset=utf-8',
      'Cache-Control': 'public, max-age=3600',
    },
  });
};
