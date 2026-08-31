import { describe, expect, it } from 'vitest';
import { productJsonLd, productBreadcrumbJsonLd, serializeJsonLd } from '../seo';
import type { ProductDto } from '../api';

const base: ProductDto = {
  id: 'p-1',
  name: 'Vestido Midi Floral',
  description: 'Vestido de verano en algodón.',
  price: { amount: 285000, currency: 'CLP' },
  imageUrl: '/api/media/products/vestido.jpg',
  condition: 'NEW',
  brand: 'Zara',
  stock: 3,
  active: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-08-01T00:00:00Z',
};

const canonicalUrl = 'https://pilarestilo.com/es/products/p-1';
const req = new URL(canonicalUrl);

describe('productJsonLd', () => {
  it('builds a Product with an Offer carrying price, currency and availability', () => {
    const ld = productJsonLd(base, { locale: 'es', canonicalUrl, requestUrl: req });
    expect(ld['@type']).toBe('Product');
    expect(ld.name).toBe('Vestido Midi Floral');
    expect(ld.sku).toBe('p-1');
    expect(ld.image).toEqual(['https://pilarestilo.com/api/media/products/vestido.jpg']);
    expect(ld.brand).toEqual({ '@type': 'Brand', name: 'Zara' });
    const offer = ld.offers as Record<string, unknown>;
    expect(offer.price).toBe(285000);
    expect(offer.priceCurrency).toBe('CLP');
    expect(offer.availability).toBe('https://schema.org/InStock');
    expect(offer.url).toBe(canonicalUrl);
  });

  it('marks OutOfStock when stock is zero', () => {
    const ld = productJsonLd({ ...base, stock: 0 }, { locale: 'es', canonicalUrl, requestUrl: req });
    const offer = ld.offers as Record<string, unknown>;
    expect(offer.availability).toBe('https://schema.org/OutOfStock');
  });

  it('uses UsedCondition for second-hand items', () => {
    const ld = productJsonLd(
      { ...base, condition: 'USED' },
      { locale: 'es', canonicalUrl, requestUrl: req },
    );
    expect(ld.itemCondition).toBe('https://schema.org/UsedCondition');
  });

  it('adds aggregateRating only when there are reviews', () => {
    expect(
      productJsonLd(base, { locale: 'es', canonicalUrl, requestUrl: req }).aggregateRating,
    ).toBeUndefined();
    const rated = productJsonLd(
      { ...base, avgRating: 4.6667, reviewCount: 12 },
      { locale: 'es', canonicalUrl, requestUrl: req },
    );
    expect(rated.aggregateRating).toEqual({
      '@type': 'AggregateRating',
      ratingValue: 4.67,
      reviewCount: 12,
    });
  });
});

describe('productBreadcrumbJsonLd', () => {
  it('lists Home, Products and the product name in order', () => {
    const ld = productBreadcrumbJsonLd(base, {
      locale: 'es',
      homeLabel: 'Inicio',
      productsLabel: 'Productos',
      requestUrl: req,
    });
    const items = ld.itemListElement as Array<Record<string, unknown>>;
    expect(items.map((i) => i.name)).toEqual(['Inicio', 'Productos', 'Vestido Midi Floral']);
    expect(items[0].item).toBe('https://pilarestilo.com/es/');
    expect(items[2].item).toBeUndefined();
  });
});

describe('serializeJsonLd', () => {
  it('escapes < so a </script> in a field cannot break out', () => {
    const out = serializeJsonLd({ name: 'a</script><script>alert(1)' });
    expect(out).not.toContain('</script>');
    expect(out).toContain('\\u003c');
  });
});
