import { describe, expect, it } from 'vitest';
import { stockIssueLabel, stockImageClass } from '../../islands/cart/StockBadge';
import type { StockIssue } from '../useStockCheck';

const soldOut: StockIssue = { type: 'SOLD_OUT', availableQty: 0, productName: 'Maxifalda' };
const short: StockIssue = { type: 'SHORT', availableQty: 2, productName: 'Maxifalda' };

describe('stockIssueLabel', () => {
  it('names the sold-out case plainly', () => {
    expect(stockIssueLabel(soldOut, 'es')).toBe('Sin stock');
    expect(stockIssueLabel(soldOut, 'en')).toBe('Out of stock');
  });

  /** A number the customer can act on: lowering the quantity fixes this line. */
  it('says how many are left for a short line', () => {
    expect(stockIssueLabel(short, 'es')).toBe('Solo quedan 2');
    expect(stockIssueLabel(short, 'en')).toBe('Only 2 left');
  });
});

describe('the needs-a-size state', () => {
  const needsVariant: StockIssue = { type: 'NEEDS_VARIANT', availableQty: 0, productName: 'Traje' };

  /** Not "sold out": the product is there, the line just names no size. */
  it('asks for the choice rather than reporting no stock', () => {
    expect(stockIssueLabel(needsVariant, 'es')).toBe('Falta elegir talla');
    expect(stockIssueLabel(needsVariant, 'en')).toBe('Choose a size');
  });

  it('does not grey the thumbnail, because the item is available', () => {
    expect(stockImageClass(needsVariant)).toBe('');
  });
});

describe('stockImageClass', () => {
  /** The thumbnail is scanned first, so a line that cannot be bought must not look buyable. */
  it('greys out a sold-out line', () => {
    expect(stockImageClass(soldOut)).toContain('grayscale');
    expect(stockImageClass(soldOut)).toContain('opacity-40');
  });

  /**
   * A short line is real and available, just not in the quantity asked for. Dimming it would
   * say "you cannot have this", which is not true.
   */
  it('leaves a short line at full strength', () => {
    expect(stockImageClass(short)).toBe('');
  });

  it('leaves an unflagged line alone', () => {
    expect(stockImageClass(undefined)).toBe('');
  });
});
