import type { DiscountCodeDto } from './api';

/**
 * Order totals, computed the same way the backend computes them.
 *
 * The customer is shown a total before the order exists, so this arithmetic has to land on
 * the same figure `Discount.computeDiscountFor` produces server-side — including the
 * rounding. CLP has no subunit: every amount here is whole pesos.
 */
export interface CheckoutTotals {
  subtotal: number;
  /** Staff-only 10%, applied before the code so a code never discounts an already-discounted line twice. */
  employeeDiscount: number;
  codeDiscount: number;
  total: number;
}

export function computeCodeDiscount(
  subtotal: number,
  discount: Pick<DiscountCodeDto, 'type' | 'value'> | null | undefined
): number {
  if (!discount) return 0;
  if (discount.type === 'PERCENTAGE') {
    return Math.round((subtotal * discount.value) / 100);
  }
  return Math.min(discount.value, subtotal);
}

export function computeTotals(
  subtotal: number,
  options: {
    isEmployee: boolean;
    discount?: Pick<DiscountCodeDto, 'type' | 'value'> | null;
  }
): CheckoutTotals {
  const employeeDiscount = options.isEmployee ? Math.round(subtotal * 0.1) : 0;
  const codeDiscount = computeCodeDiscount(subtotal, options.discount);

  return {
    subtotal,
    employeeDiscount,
    codeDiscount,
    /** Never negative: a fixed code larger than the cart must not turn into a refund. */
    total: Math.max(0, subtotal - employeeDiscount - codeDiscount),
  };
}
