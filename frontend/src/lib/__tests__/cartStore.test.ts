import { describe, it, expect, vi, beforeEach } from 'vitest';
import { verifyStockForItem } from '../cartStore';
import * as api from '../api';

vi.mock('../api', () => ({
  getProduct: vi.fn(),
}));

const mockGetProduct = vi.mocked(api.getProduct);

function makeProduct(overrides: Partial<Parameters<typeof api.getProduct>[0] extends string ? ReturnType<typeof api.getProduct> extends Promise<infer T> ? T : never : never> = {}) {
  return {
    id: 'p1',
    name: 'Producto Test',
    description: '',
    price: { amount: 10000, currency: 'CLP' },
    imageUrl: '',
    condition: 'NEW' as const,
    brand: 'Test',
    stock: 10,
    active: true,
    createdAt: '',
    updatedAt: '',
    variants: [],
    ...overrides,
  };
}

function makeVariant(color: string, size: string, stockAvailable: number) {
  return { color, size, stock: stockAvailable, stockOnHand: stockAvailable, stockReserved: 0, stockAvailable };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('verifyStockForItem', () => {
  it('returns ok when variant qty <= stockAvailable', async () => {
    mockGetProduct.mockResolvedValue(makeProduct({
      variants: [makeVariant('negro', 'M', 5)],
    }) as any);

    const result = await verifyStockForItem('p1', { color: 'negro', size: 'M' }, 3);
    expect(result).toEqual({ ok: true, verified: true });
  });

  it('returns fail with availableQty when variant qty > stockAvailable', async () => {
    mockGetProduct.mockResolvedValue(makeProduct({
      name: 'Camisa Lino',
      variants: [makeVariant('blanco', 'S', 2)],
    }) as any);

    const result = await verifyStockForItem('p1', { color: 'blanco', size: 'S' }, 5);
    expect(result).toEqual({ ok: false, reason: 'INSUFFICIENT', availableQty: 2, productName: 'Camisa Lino' });
  });

  it('returns ok at exact stockAvailable boundary', async () => {
    mockGetProduct.mockResolvedValue(makeProduct({
      variants: [makeVariant('rojo', 'L', 3)],
    }) as any);

    const result = await verifyStockForItem('p1', { color: 'rojo', size: 'L' }, 3);
    expect(result).toEqual({ ok: true, verified: true });
  });

  it('returns fail with availableQty=0 when variant not found', async () => {
    mockGetProduct.mockResolvedValue(makeProduct({
      name: 'Vestido',
      variants: [makeVariant('rojo', 'L', 3)],
    }) as any);

    const result = await verifyStockForItem('p1', { color: 'azul', size: 'M' }, 1);
    expect(result).toEqual({ ok: false, reason: 'INSUFFICIENT', availableQty: 0, productName: 'Vestido' });
  });

  it('uses product.stock when no variants and no variant arg', async () => {
    mockGetProduct.mockResolvedValue(makeProduct({ stock: 8, variants: [] }) as any);

    const result = await verifyStockForItem('p1', null, 5);
    expect(result).toEqual({ ok: true, verified: true });
  });

  it('fails when requested qty > product.stock (no variants)', async () => {
    mockGetProduct.mockResolvedValue(makeProduct({ name: 'Accesorio', stock: 3, variants: [] }) as any);

    const result = await verifyStockForItem('p1', null, 5);
    expect(result).toEqual({ ok: false, reason: 'INSUFFICIENT', availableQty: 3, productName: 'Accesorio' });
  });

  /**
   * The line a customer reached the pay button with. The product grid offered a plain add
   * button for a product sold by size, so the cart line carried none; this check then measured
   * it against products.stock — the sum across every variant — and called it available, while
   * inventory-service refuses any reservation for a variant product with no size. The refusal
   * arrived as "Inventory reservation rejected (status 400)" after the whole checkout.
   */
  it('refuses a line naming no variant when the product has several', async () => {
    mockGetProduct.mockResolvedValue(makeProduct({
      name: 'Traje Pantalón Marino',
      stock: 2,
      variants: [makeVariant('Marino', '38', 1), makeVariant('Marino', '40', 1)],
    }) as any);

    const result = await verifyStockForItem('p1', null, 1);

    expect(result).toEqual({
      ok: false,
      reason: 'NEEDS_VARIANT',
      productName: 'Traje Pantalón Marino',
    });
  });

  /**
   * One variant is not a choice. Twelve of the seventeen products in this catalogue are a single
   * Base/UNICO row, so a line naming no variant can only mean that one — asking the customer to
   * pick from a list of one is friction, not clarity.
   */
  it('resolves a variant-less line when the product has exactly one variant', async () => {
    mockGetProduct.mockResolvedValue(makeProduct({
      name: 'Blazer Clásico Crema',
      stock: 5,
      variants: [makeVariant('Base', 'UNICO', 5)],
    }) as any);

    expect(await verifyStockForItem('p1', null, 2)).toEqual({ ok: true, verified: true });
  });

  it('still reports the shortfall when that single variant runs low', async () => {
    mockGetProduct.mockResolvedValue(makeProduct({
      name: 'Blazer Clásico Crema',
      stock: 5,
      variants: [makeVariant('Base', 'UNICO', 1)],
    }) as any);

    expect(await verifyStockForItem('p1', null, 3)).toEqual({
      ok: false,
      reason: 'INSUFFICIENT',
      availableQty: 1,
      productName: 'Blazer Clásico Crema',
    });
  });

  it('reports unverified — not available — when the API is unreachable', async () => {
    mockGetProduct.mockRejectedValue(new Error('Network error'));

    const result = await verifyStockForItem('p1', null, 1);
    expect(result).toEqual({ ok: true, verified: false });
  });

  it('reports unverified when the product lookup 404s', async () => {
    mockGetProduct.mockRejectedValue(new Error('Product p1 not found'));

    const result = await verifyStockForItem('p1', { color: 'negro', size: 'M' }, 2);
    expect(result).toEqual({ ok: true, verified: false });
  });
});
