import { beforeEach, describe, expect, it, vi } from 'vitest';
import { checkStockForItems, pruneResolvedIssues, type StockIssues } from '../useStockCheck';
import type { CartItem } from '../cartStore';

const mockVerify = vi.hoisted(() => vi.fn());
vi.mock('../cartStore', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../cartStore')>()),
  verifyStockForItem: mockVerify,
}));

function line(id: string, quantity = 1, extra: Partial<CartItem> = {}): CartItem {
  return {
    id,
    productId: '11111111-1111-1111-1111-111111111111',
    name: 'Vestido',
    brand: 'PE',
    price: { amount: 10000, currency: 'CLP' },
    imageUrl: '',
    condition: 'NEW',
    quantity,
    ...extra,
  };
}

describe('checkStockForItems', () => {
  beforeEach(() => mockVerify.mockReset());

  it('checks every line', async () => {
    mockVerify.mockResolvedValue({ ok: true, verified: true });

    await checkStockForItems([line('a'), line('b')]);

    expect(mockVerify).toHaveBeenCalledTimes(2);
  });

  it('reports nothing when every line is available', async () => {
    mockVerify.mockResolvedValue({ ok: true, verified: true });

    expect(await checkStockForItems([line('a')])).toEqual({});
  });

  it('marks a line with no stock left as SOLD_OUT', async () => {
    mockVerify.mockResolvedValue({ ok: false, reason: 'INSUFFICIENT', availableQty: 0, productName: 'Vestido' });

    const issues = await checkStockForItems([line('a')]);

    expect(issues.a).toEqual({ type: 'SOLD_OUT', availableQty: 0, productName: 'Vestido' });
  });

  /** Different message, different remedy: SHORT can be fixed by lowering the quantity. */
  it('marks a line with some stock left as SHORT', async () => {
    mockVerify.mockResolvedValue({ ok: false, reason: 'INSUFFICIENT', availableQty: 2, productName: 'Vestido' });

    const issues = await checkStockForItems([line('a', 5)]);

    expect(issues.a.type).toBe('SHORT');
    expect(issues.a.availableQty).toBe(2);
  });

  /**
   * The whole reason verifyStockForItem reports `verified`. Flagging a line because a request
   * failed would talk a customer out of a purchase the backend would have accepted.
   */
  it('leaves a line alone when the check could not run', async () => {
    mockVerify.mockResolvedValue({ ok: true, verified: false });

    expect(await checkStockForItems([line('a')])).toEqual({});
  });

  it('keys issues by cart line, so one variant does not flag another', async () => {
    mockVerify
      .mockResolvedValueOnce({ ok: false, reason: 'INSUFFICIENT', availableQty: 0, productName: 'Vestido' })
      .mockResolvedValueOnce({ ok: true, verified: true });

    const issues = await checkStockForItems([
      line('p::rojo::M', 1, { variantColor: 'Rojo', variantSize: 'M' }),
      line('p::azul::L', 1, { variantColor: 'Azul', variantSize: 'L' }),
    ]);

    expect(issues['p::rojo::M']).toBeDefined();
    expect(issues['p::azul::L']).toBeUndefined();
  });

  it('passes the variant through to the check', async () => {
    mockVerify.mockResolvedValue({ ok: true, verified: true });

    await checkStockForItems([line('a', 3, { variantColor: 'Rojo', variantSize: 'M' })]);

    expect(mockVerify).toHaveBeenCalledWith(
      '11111111-1111-1111-1111-111111111111',
      { color: 'Rojo', size: 'M' },
      3
    );
  });

  it('sends no variant for a product that has none', async () => {
    mockVerify.mockResolvedValue({ ok: true, verified: true });

    await checkStockForItems([line('a')]);

    expect(mockVerify).toHaveBeenCalledWith(expect.any(String), null, 1);
  });

  /**
   * The defect a customer hit at the pay button: the grid offered a plain add button for a
   * product that sells by size, so the line carried none. verifyStockForItem then measured it
   * against products.stock — the sum across every variant — and said it was available, while
   * inventory-service refuses any reservation for a variant product with no size.
   */
  it('flags a line that names no variant for a product sold by variant', async () => {
    mockVerify.mockResolvedValue({ ok: false, reason: 'NEEDS_VARIANT', productName: 'Traje' });

    const issues = await checkStockForItems([line('a')]);

    expect(issues.a.type).toBe('NEEDS_VARIANT');
    expect(issues.a.productName).toBe('Traje');
  });

  it('does nothing for an empty cart', async () => {
    expect(await checkStockForItems([])).toEqual({});
    expect(mockVerify).not.toHaveBeenCalled();
  });
});

describe('pruneResolvedIssues', () => {
  const soldOut: StockIssues = { a: { type: 'SOLD_OUT', availableQty: 0, productName: 'V' } };
  const short: StockIssues = { a: { type: 'SHORT', availableQty: 2, productName: 'V' } };

  it('drops an issue for a line that left the cart', () => {
    expect(pruneResolvedIssues(soldOut, [line('b')])).toEqual({});
  });

  it('drops a SHORT issue once the quantity fits what is available', () => {
    expect(pruneResolvedIssues(short, [line('a', 2)])).toEqual({});
  });

  it('keeps a SHORT issue while the quantity still exceeds stock', () => {
    expect(pruneResolvedIssues(short, [line('a', 3)]).a).toBeDefined();
  });

  /** Lowering the quantity cannot fix a line with nothing left. */
  it('keeps a SOLD_OUT issue whatever the quantity', () => {
    expect(pruneResolvedIssues(soldOut, [line('a', 1)]).a).toBeDefined();
  });

  /** Same reference when nothing changed, so React skips the render. */
  it('returns the identical object when nothing resolved', () => {
    const items = [line('a', 3)];
    expect(pruneResolvedIssues(short, items)).toBe(short);
  });
});
