import type { ProductDto, ProductVariantDto } from './api';
import { absoluteUrl } from './siteUrl';

/**
 * Builds the Google Merchant Center product feed (RSS 2.0 + the `g:` namespace) so the catalogue
 * is submitted with an explicit target country, currency and per-product attributes instead of
 * left to Merchant Center's "found by Google" crawler, which mis-targeted the store to Spain and
 * guessed the currency.
 *
 * Country and currency: `g:price` carries the ISO currency ("4500 CLP"); the target country is set
 * on the data source in Merchant Center, not in the file. Shipping is left to the Merchant Center
 * shipping policy rather than repeated per item.
 */

// Google product category IDs (numeric ids are stable across taxonomy revisions).
const GOOGLE_CATEGORY_BY_TYPE: Record<string, string> = {
  CLOTHING: '1604', // Apparel & Accessories > Clothing
  SHOES: '187', // Apparel & Accessories > Shoes
  JEWELRY: '188', // Apparel & Accessories > Jewelry
  ACCESSORY: '167', // Apparel & Accessories > Clothing Accessories
};
const DEFAULT_GOOGLE_CATEGORY = '166'; // Apparel & Accessories

// A single seeded variant of "Base" / "UNICO" means "this product has no real variant axis".
const SENTINEL_COLOR = new Set(['base', 'default', 'unica', 'única', 'n/a', '-', '']);
const SENTINEL_SIZE = new Set(['unico', 'único', 'talla unica', 'talla única', 'default', 'n/a', '-', '']);

export interface MerchantFeedOptions {
  siteUrl: string;
  headers?: Headers;
  /** 'female' | 'male' | 'unisex' — the catalogue is women's fashion, so 'female' by default. */
  gender?: string;
}

export function googleProductCategory(product: ProductDto): string {
  for (const type of product.categoryTypes ?? []) {
    const mapped = GOOGLE_CATEGORY_BY_TYPE[type];
    if (mapped) return mapped;
  }
  return DEFAULT_GOOGLE_CATEGORY;
}

function xmlEscape(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function truncate(value: string, max: number): string {
  const clean = value.replace(/\s+/g, ' ').trim();
  return clean.length > max ? `${clean.slice(0, max - 1)}…` : clean;
}

function idSafe(value: string): string {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'x';
}

// Google Merchant Center rejects `id` past 50 characters. Most variant suffixes fit easily, but a
// long color/size name (e.g. "Pata de Gallo") on top of a 36-char UUID product id can push past
// the limit — this was flagging real products ("Valor demasiado largo en el atributo: id").
const MAX_ITEM_ID_LENGTH = 50;

function shortHash(value: string): string {
  let hash = 0;
  for (let i = 0; i < value.length; i += 1) {
    hash = (hash * 31 + value.charCodeAt(i)) >>> 0;
  }
  return hash.toString(36);
}

/**
 * Falls back to a short deterministic hash of the suffix when the full id would exceed Google's
 * limit, rather than truncating it — truncation risks two differently-named variants (e.g. same
 * long color, different size) colliding onto the same shortened id.
 */
function buildItemId(productId: string, idSuffix: string): string {
  const full = productId + idSuffix;
  if (full.length <= MAX_ITEM_ID_LENGTH) return full;
  return `${productId}-${shortHash(idSuffix)}`;
}

function realColor(color: string | undefined): string | null {
  const c = (color ?? '').trim();
  return c && !SENTINEL_COLOR.has(c.toLowerCase()) ? c : null;
}

function realSize(size: string | undefined): string | null {
  const s = (size ?? '').trim();
  return s && !SENTINEL_SIZE.has(s.toLowerCase()) ? s : null;
}

interface ItemInput {
  idSuffix: string;
  groupId: string | null;
  color: string | null;
  size: string;
  inStock: boolean;
}

function renderItem(product: ProductDto, opts: MerchantFeedOptions, v: ItemInput): string {
  const link = `${opts.siteUrl}/es/products/${product.id}`;
  const image = absoluteUrl(product.imageUrl, opts.siteUrl, opts.headers);
  const additionalImages = (product.galleryImageUrls ?? [])
    .slice(0, 10)
    .map((u) => absoluteUrl(u, opts.siteUrl, opts.headers));

  const hasSale =
    !!product.listPrice &&
    product.listPrice.currency === product.price.currency &&
    product.listPrice.amount > product.price.amount;
  const regular = hasSale ? product.listPrice! : product.price;
  const sale = hasSale ? product.price : null;

  const parts: string[] = [
    `<g:id>${xmlEscape(buildItemId(product.id, v.idSuffix))}</g:id>`,
    v.groupId ? `<g:item_group_id>${xmlEscape(v.groupId)}</g:item_group_id>` : '',
    `<g:title>${xmlEscape(truncate(product.name, 150))}</g:title>`,
    `<g:description>${xmlEscape(truncate(product.description || product.name, 5000))}</g:description>`,
    `<g:link>${xmlEscape(link)}</g:link>`,
    `<g:image_link>${xmlEscape(image)}</g:image_link>`,
    ...additionalImages.map((u) => `<g:additional_image_link>${xmlEscape(u)}</g:additional_image_link>`),
    `<g:availability>${v.inStock ? 'in_stock' : 'out_of_stock'}</g:availability>`,
    `<g:price>${regular.amount} ${regular.currency}</g:price>`,
    sale ? `<g:sale_price>${sale.amount} ${sale.currency}</g:sale_price>` : '',
    `<g:condition>${product.condition === 'USED' ? 'used' : 'new'}</g:condition>`,
    `<g:brand>${xmlEscape(product.brand || 'Pilar Estilo')}</g:brand>`,
    '<g:identifier_exists>no</g:identifier_exists>',
    `<g:google_product_category>${googleProductCategory(product)}</g:google_product_category>`,
    '<g:age_group>adult</g:age_group>',
    `<g:gender>${opts.gender ?? 'female'}</g:gender>`,
    v.color ? `<g:color>${xmlEscape(v.color)}</g:color>` : '',
    `<g:size>${xmlEscape(v.size)}</g:size>`,
  ].filter(Boolean);

  return `  <item>\n    ${parts.join('\n    ')}\n  </item>`;
}

/**
 * One `<item>` per real variant (sharing `g:item_group_id`), or a single item when the product
 * has no variant axis. Skips products with no price.
 */
export function merchantFeedItems(product: ProductDto, opts: MerchantFeedOptions): string[] {
  if (!product.price || product.price.amount <= 0) return [];

  const variants: ProductVariantDto[] = (product.variants ?? []).filter(
    (variant) => realColor(variant.color) !== null || realSize(variant.size) !== null,
  );

  if (variants.length === 0) {
    return [
      renderItem(product, opts, {
        idSuffix: '',
        groupId: null,
        color: null,
        size: 'one size',
        inStock: product.stock > 0,
      }),
    ];
  }

  return variants.map((variant) =>
    renderItem(product, opts, {
      idSuffix: `-${idSafe(variant.color)}-${idSafe(variant.size)}`,
      groupId: product.id,
      color: realColor(variant.color),
      size: realSize(variant.size) ?? 'one size',
      inStock: (variant.stockAvailable ?? variant.stock ?? 0) > 0,
    }),
  );
}

/** Wraps the item blocks in the RSS 2.0 channel envelope. */
export function merchantFeedXml(itemBlocks: string[], opts: { siteUrl: string; title?: string }): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:g="http://base.google.com/ns/1.0">
  <channel>
    <title>${xmlEscape(opts.title ?? 'Pilar Estilo')}</title>
    <link>${xmlEscape(opts.siteUrl)}</link>
    <description>Catálogo Pilar Estilo para Google Merchant Center</description>
${itemBlocks.join('\n')}
  </channel>
</rss>
`;
}
