import { describe, expect, it } from 'vitest';
import { merchantFeedItems, merchantFeedXml, googleProductCategory } from '../merchantFeed';
import type { ProductDto } from '../api';

const base: ProductDto = {
  id: 'p-1',
  name: 'Vestido Midi Floral',
  description: 'Vestido de verano en algodón, tendencia 2026.',
  price: { amount: 28500, currency: 'CLP' },
  imageUrl: '/api/media/products/vestido.jpg',
  condition: 'NEW',
  brand: 'Zara',
  stock: 4,
  active: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-08-01T00:00:00Z',
  categoryTypes: ['CLOTHING'],
};

const opts = { siteUrl: 'https://pilarestilo.com' };

describe('merchantFeedItems — product with no real variant axis', () => {
  it('emits one item with price+currency, availability, link, condition, category', () => {
    const [item] = merchantFeedItems(base, opts);
    expect(item).toContain('<g:id>p-1</g:id>');
    expect(item).not.toContain('<g:item_group_id>');
    expect(item).toContain('<g:price>28500 CLP</g:price>');
    expect(item).toContain('<g:availability>in_stock</g:availability>');
    expect(item).toContain('<g:link>https://pilarestilo.com/es/products/p-1</g:link>');
    expect(item).toContain('<g:image_link>https://pilarestilo.com/api/media/products/vestido.jpg</g:image_link>');
    expect(item).toContain('<g:condition>new</g:condition>');
    expect(item).toContain('<g:brand>Zara</g:brand>');
    expect(item).toContain('<g:google_product_category>1604</g:google_product_category>');
    expect(item).toContain('<g:size>one size</g:size>');
    expect(item).toContain('<g:gender>female</g:gender>');
  });

  it('treats a single Base/UNICO variant as no variant axis', () => {
    const items = merchantFeedItems(
      { ...base, variants: [{ color: 'Base', size: 'UNICO', stock: 4, stockOnHand: 4, stockReserved: 0, stockAvailable: 4 }] },
      opts,
    );
    expect(items).toHaveLength(1);
    expect(items[0]).not.toContain('<g:color>');
  });

  it('marks out_of_stock when the aggregate stock is zero', () => {
    const [item] = merchantFeedItems({ ...base, stock: 0 }, opts);
    expect(item).toContain('<g:availability>out_of_stock</g:availability>');
  });

  it('puts the list price as g:price and the current price as g:sale_price when discounted', () => {
    const [item] = merchantFeedItems(
      { ...base, price: { amount: 20000, currency: 'CLP' }, listPrice: { amount: 28500, currency: 'CLP' } },
      opts,
    );
    expect(item).toContain('<g:price>28500 CLP</g:price>');
    expect(item).toContain('<g:sale_price>20000 CLP</g:sale_price>');
  });
});

describe('merchantFeedItems — product with real variants', () => {
  const varied: ProductDto = {
    ...base,
    id: 'p-2',
    variants: [
      { color: 'Rojo', size: 'S', stock: 2, stockOnHand: 2, stockReserved: 0, stockAvailable: 2 },
      { color: 'Rojo', size: 'M', stock: 0, stockOnHand: 1, stockReserved: 1, stockAvailable: 0 },
      { color: 'Azul', size: 'L', stock: 3, stockOnHand: 3, stockReserved: 0, stockAvailable: 3 },
    ],
  };

  it('emits one item per variant, sharing item_group_id', () => {
    const items = merchantFeedItems(varied, opts);
    expect(items).toHaveLength(3);
    for (const item of items) expect(item).toContain('<g:item_group_id>p-2</g:item_group_id>');
    expect(items[0]).toContain('<g:id>p-2-rojo-s</g:id>');
    expect(items[0]).toContain('<g:color>Rojo</g:color>');
    expect(items[0]).toContain('<g:size>S</g:size>');
  });

  it('reflects per-variant availability', () => {
    const items = merchantFeedItems(varied, opts);
    expect(items[0]).toContain('<g:availability>in_stock</g:availability>'); // Rojo S
    expect(items[1]).toContain('<g:availability>out_of_stock</g:availability>'); // Rojo M, all reserved
  });

  it('keeps g:id at or under Google\'s 50-character limit for long color/size names', () => {
    // Real production case: a 36-char UUID plus a long color name (e.g. "Pata de Gallo") pushed
    // the id past 50 chars, and Merchant Center flagged it as "Valor demasiado largo".
    const longColorProduct: ProductDto = {
      ...base,
      id: '073c7724-24b7-4594-b153-806b7daa5ee9',
      variants: [
        { color: 'Pata de Gallo', size: 'M', stock: 2, stockOnHand: 2, stockReserved: 0, stockAvailable: 2 },
        { color: 'Pata de Gallo', size: 'L', stock: 2, stockOnHand: 2, stockReserved: 0, stockAvailable: 2 },
      ],
    };

    const items = merchantFeedItems(longColorProduct, opts);
    const ids = items.map((item) => /<g:id>(.*?)<\/g:id>/.exec(item)?.[1]);

    for (const id of ids) {
      expect(id).toBeDefined();
      expect(id!.length).toBeLessThanOrEqual(50);
    }
    // Different variants of the same long-named color must not collapse onto the same id.
    expect(new Set(ids).size).toBe(2);
    // item_group_id still ties them to the same product regardless of how g:id was shortened.
    for (const item of items) expect(item).toContain(`<g:item_group_id>${longColorProduct.id}</g:item_group_id>`);
  });
});

describe('googleProductCategory', () => {
  it('maps the category type, falling back to Apparel & Accessories', () => {
    expect(googleProductCategory({ ...base, categoryTypes: ['SHOES'] })).toBe('187');
    expect(googleProductCategory({ ...base, categoryTypes: ['JEWELRY'] })).toBe('188');
    expect(googleProductCategory({ ...base, categoryTypes: ['GENERIC'] })).toBe('166');
    expect(googleProductCategory({ ...base, categoryTypes: undefined })).toBe('166');
  });
});

describe('merchantFeedItems — guards', () => {
  it('skips a product with no usable price', () => {
    expect(merchantFeedItems({ ...base, price: { amount: 0, currency: 'CLP' } }, opts)).toEqual([]);
  });

  it('escapes XML-unsafe characters in the title and description', () => {
    const [item] = merchantFeedItems({ ...base, name: 'Falda <b>"roja"</b> & más', description: 'a < b' }, opts);
    expect(item).toContain('Falda &lt;b&gt;&quot;roja&quot;&lt;/b&gt; &amp; más');
    expect(item).not.toContain('<b>');
  });
});

describe('merchantFeedXml', () => {
  it('wraps items in the RSS 2.0 channel with the g namespace', () => {
    const xml = merchantFeedXml(merchantFeedItems(base, opts), { siteUrl: 'https://pilarestilo.com' });
    expect(xml).toContain('<rss version="2.0" xmlns:g="http://base.google.com/ns/1.0">');
    expect(xml).toContain('<channel>');
    expect(xml).toContain('<link>https://pilarestilo.com</link>');
    expect(xml).toContain('<g:id>p-1</g:id>');
  });
});
