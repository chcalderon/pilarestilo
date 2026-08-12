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
    expect(result).toEqual({ ok: false, availableQty: 2, productName: 'Camisa Lino' });
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
    expect(result).toEqual({ ok: false, availableQty: 0, productName: 'Vestido' });
  });

  it('uses product.stock when no variants and no variant arg', async () => {
    mockGetProduct.mockResolvedValue(makeProduct({ stock: 8, variants: [] }) as any);

    const result = await verifyStockForItem('p1', null, 5);
    expect(result).toEqual({ ok: true, verified: true });
  });

  it('fails when requested qty > product.stock (no variants)', async () => {
    mockGetProduct.mockResolvedValue(makeProduct({ name: 'Accesorio', stock: 3, variants: [] }) as any);

    const result = await verifyStockForItem('p1', null, 5);
    expect(result).toEqual({ ok: false, availableQty: 3, productName: 'Accesorio' });
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
