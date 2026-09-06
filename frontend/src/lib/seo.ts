import type { ProductDto } from './api';
import { absoluteUrl } from './siteUrl';
import type { Locale } from '../i18n/index';

/**
 * schema.org JSON-LD for a product detail page. Kept as a pure builder so the shape is
 * unit-tested rather than eyeballed in rendered HTML. Google needs, at minimum, `name` plus
 * `offers`; `image`, `price`/`priceCurrency` and `availability` are what make it eligible for
 * free Shopping listings.
 */
export function productJsonLd(
  product: ProductDto,
  opts: { locale: Locale; canonicalUrl: string; requestUrl?: URL | string; headers?: Headers },
): Record<string, unknown> {
  const inStock = product.stock > 0;
  const condition =
    product.condition === 'USED'
      ? 'https://schema.org/UsedCondition'
      : 'https://schema.org/NewCondition';

  const imageUrls = [product.imageUrl, ...(product.galleryImageUrls ?? [])]
    .map((u) => (u ?? '').trim())
    .filter(Boolean);
  const uniqueImages = [...new Set(imageUrls)].map((u) =>
    absoluteUrl(u, opts.requestUrl, opts.headers),
  );

  const jsonLd: Record<string, unknown> = {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: product.name,
    description: product.description,
    image: uniqueImages,
    sku: product.id,
    brand: { '@type': 'Brand', name: product.brand },
    itemCondition: condition,
    offers: {
      '@type': 'Offer',
      url: opts.canonicalUrl,
      priceCurrency: product.price.currency,
      price: product.price.amount,
      availability: inStock
        ? 'https://schema.org/InStock'
        : 'https://schema.org/OutOfStock',
      itemCondition: condition,
    },
  };

  if ((product.reviewCount ?? 0) > 0 && (product.avgRating ?? 0) > 0) {
    jsonLd.aggregateRating = {
      '@type': 'AggregateRating',
      ratingValue: Number((product.avgRating ?? 0).toFixed(2)),
      reviewCount: product.reviewCount,
    };
  }

  return jsonLd;
}

/** BreadcrumbList JSON-LD: Home › Products › {name}. */
export function productBreadcrumbJsonLd(
  product: ProductDto,
  opts: {
    locale: Locale;
    productsLabel: string;
    homeLabel: string;
    requestUrl?: URL | string;
    headers?: Headers;
  },
): Record<string, unknown> {
  const { locale } = opts;
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      {
        '@type': 'ListItem',
        position: 1,
        name: opts.homeLabel,
        item: absoluteUrl(`/${locale}/`, opts.requestUrl, opts.headers),
      },
      {
        '@type': 'ListItem',
        position: 2,
        name: opts.productsLabel,
        item: absoluteUrl(`/${locale}/products`, opts.requestUrl, opts.headers),
      },
      {
        '@type': 'ListItem',
        position: 3,
        name: product.name,
      },
    ],
  };
}

/**
 * Organization JSON-LD for the home page — feeds Google's knowledge panel and ties the storefront
 * to its social accounts.
 */
export function organizationJsonLd(opts: {
  siteUrl: string;
  instagramUrl?: string | null;
  facebookUrl?: string | null;
}): Record<string, unknown> {
  const sameAs = [opts.instagramUrl, opts.facebookUrl].filter(
    (u): u is string => typeof u === 'string' && u.length > 0,
  );
  const ld: Record<string, unknown> = {
    '@context': 'https://schema.org',
    '@type': 'OnlineStore',
    name: 'Pilar Estilo',
    url: opts.siteUrl,
    logo: `${opts.siteUrl}/logo-full.png`,
  };
  if (sameAs.length > 0) ld.sameAs = sameAs;
  return ld;
}

/** `<script type="application/ld+json">` payload — JSON with the HTML-unsafe characters escaped. */
export function serializeJsonLd(data: Record<string, unknown>): string {
  return JSON.stringify(data).replace(/</g, '\\u003c');
}
