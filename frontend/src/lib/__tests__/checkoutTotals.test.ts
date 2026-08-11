import { describe, expect, it } from 'vitest';
import { computeCodeDiscount, computeTotals } from '../checkoutTotals';

const pct = (value: number) => ({ type: 'PERCENTAGE' as const, value });
const fixed = (value: number) => ({ type: 'FIXED' as const, value });

describe('computeCodeDiscount', () => {
  it('takes a percentage of the subtotal', () => {
    expect(computeCodeDiscount(100000, pct(10))).toBe(10000);
  });

  /**
   * Mirrors Discount.percentage_discount_rounds_to_whole_pesos on the backend. If these two
   * ever disagree the customer is shown one total and charged another.
   */
  it('rounds to whole pesos, like the backend', () => {
    expect(computeCodeDiscount(33333, pct(10))).toBe(3333);
    expect(computeCodeDiscount(33335, pct(10))).toBe(3334);
  });

  it('caps a fixed code at the subtotal', () => {
    expect(computeCodeDiscount(3000, fixed(5000))).toBe(3000);
    expect(computeCodeDiscount(50000, fixed(5000))).toBe(5000);
  });

  it('is zero without a code', () => {
    expect(computeCodeDiscount(50000, null)).toBe(0);
    expect(computeCodeDiscount(50000, undefined)).toBe(0);
  });
});

describe('computeTotals', () => {
  it('subtracts nothing for a regular customer without a code', () => {
    expect(computeTotals(50000, { isEmployee: false })).toEqual({
      subtotal: 50000,
      employeeDiscount: 0,
      codeDiscount: 0,
      total: 50000,
    });
  });

  it('applies the staff discount', () => {
    const t = computeTotals(50000, { isEmployee: true });
    expect(t.employeeDiscount).toBe(5000);
    expect(t.total).toBe(45000);
  });

  it('stacks the staff discount and a code, both off the subtotal', () => {
    const t = computeTotals(50000, { isEmployee: true, discount: pct(10) });
    expect(t.employeeDiscount).toBe(5000);
    expect(t.codeDiscount).toBe(5000);
    expect(t.total).toBe(40000);
  });

  /** A code worth more than the cart is a free order, never a negative one. */
  it('floors the total at zero', () => {
    const t = computeTotals(3000, { isEmployee: true, discount: fixed(99000) });
    expect(t.total).toBe(0);
  });

  it('handles an empty cart', () => {
    expect(computeTotals(0, { isEmployee: true, discount: pct(50) }).total).toBe(0);
  });
});
