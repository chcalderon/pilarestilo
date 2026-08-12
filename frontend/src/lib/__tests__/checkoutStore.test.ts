import { beforeEach, describe, expect, it } from 'vitest';
import {
  CHECKOUT_STEPS,
  slugToStep,
  stepIndex,
  stepToSlug,
  useCheckoutStore,
} from '../checkoutStore';

function reset() {
  useCheckoutStore.getState().reset();
}

describe('checkout step machine', () => {
  beforeEach(reset);

  it('starts on shipping', () => {
    expect(useCheckoutStore.getState().step).toBe('shipping');
    expect(useCheckoutStore.getState().furthestStep).toBe('shipping');
  });

  it('advances one step at a time', () => {
    const { completeStep } = useCheckoutStore.getState();
    completeStep('shipping');
    expect(useCheckoutStore.getState().step).toBe('payment');
    useCheckoutStore.getState().completeStep('payment');
    expect(useCheckoutStore.getState().step).toBe('review');
  });

  /** A double-click or a stale button must not skip an unanswered step. */
  it('ignores completeStep for a step that is not the current one', () => {
    useCheckoutStore.getState().completeStep('payment');
    expect(useCheckoutStore.getState().step).toBe('shipping');
  });

  it('does not advance past the last step', () => {
    const s = useCheckoutStore.getState();
    s.completeStep('shipping');
    useCheckoutStore.getState().completeStep('payment');
    useCheckoutStore.getState().completeStep('review');
    expect(useCheckoutStore.getState().step).toBe('review');
  });

  it('remembers the furthest step when the customer goes back', () => {
    useCheckoutStore.getState().completeStep('shipping');
    useCheckoutStore.getState().completeStep('payment');
    useCheckoutStore.getState().setStep('shipping');

    expect(useCheckoutStore.getState().step).toBe('shipping');
    expect(useCheckoutStore.getState().furthestStep).toBe('review');
  });

  it('reset clears both the step and the collected answers', () => {
    const s = useCheckoutStore.getState();
    s.setShipping({ zoneCode: 'RM', courierId: 'c1', addressId: 'a1' });
    s.setPaymentMethod('WEBPAY');
    s.setDiscountCode('VERANO');
    s.completeStep('shipping');

    useCheckoutStore.getState().reset();

    const after = useCheckoutStore.getState();
    expect(after.step).toBe('shipping');
    expect(after.furthestStep).toBe('shipping');
    expect(after.shippingZoneCode).toBe('');
    expect(after.shippingAddressId).toBe('');
    expect(after.paymentMethod).toBe('TRANSFER');
    expect(after.discountCode).toBe('');
  });
});

describe('checkout answers', () => {
  beforeEach(reset);

  /** Each step writes only its own field, so a partial update cannot wipe a sibling. */
  it('setShipping leaves untouched fields alone', () => {
    useCheckoutStore.getState().setShipping({ zoneCode: 'RM', courierId: 'c1', addressId: 'a1' });
    useCheckoutStore.getState().setShipping({ courierId: 'c2' });

    const s = useCheckoutStore.getState();
    expect(s.shippingZoneCode).toBe('RM');
    expect(s.shippingCourierId).toBe('c2');
    expect(s.shippingAddressId).toBe('a1');
  });

  it('keeps the discount code but never a validated discount', () => {
    useCheckoutStore.getState().setDiscountCode('VERANO');
    expect(useCheckoutStore.getState().discountCode).toBe('VERANO');
    expect(useCheckoutStore.getState()).not.toHaveProperty('appliedDiscount');
  });
});

describe('step slugs', () => {
  it('round-trips every step through its URL slug', () => {
    for (const step of CHECKOUT_STEPS) {
      expect(slugToStep(stepToSlug(step))).toBe(step);
    }
  });

  it('rejects an unknown or missing slug', () => {
    expect(slugToStep('pagos')).toBeNull();
    expect(slugToStep('')).toBeNull();
    expect(slugToStep(null)).toBeNull();
  });

  it('is case-insensitive, because URLs get hand-edited', () => {
    expect(slugToStep('ENVIO')).toBe('shipping');
  });

  it('orders the steps shipping → payment → review', () => {
    expect(stepIndex('shipping')).toBeLessThan(stepIndex('payment'));
    expect(stepIndex('payment')).toBeLessThan(stepIndex('review'));
  });
});

describe('persisted state is treated as untrusted', () => {
  beforeEach(reset);

  /**
   * localStorage is user-editable. A furthest marker behind the current step would let
   * the indicator mark the current step unreachable and strand the customer.
   */
  it('pulls furthestStep forward when it trails the current step', () => {
    const merge = useCheckoutStore.persist.getOptions().merge!;
    const merged = merge(
      { step: 'review', furthestStep: 'shipping' },
      useCheckoutStore.getState()
    ) as { step: string; furthestStep: string };

    expect(merged.step).toBe('review');
    expect(merged.furthestStep).toBe('review');
  });

  it('falls back to shipping for an unknown step', () => {
    const merge = useCheckoutStore.persist.getOptions().merge!;
    const merged = merge({ step: 'wat', furthestStep: 'wat' }, useCheckoutStore.getState()) as {
      step: string;
    };

    expect(merged.step).toBe('shipping');
  });

  it('coerces a bogus payment method to TRANSFER', () => {
    const merge = useCheckoutStore.persist.getOptions().merge!;
    const merged = merge({ paymentMethod: 'CRYPTO' }, useCheckoutStore.getState()) as {
      paymentMethod: string;
    };

    expect(merged.paymentMethod).toBe('TRANSFER');
  });

  it('coerces non-string answers to empty strings', () => {
    const merge = useCheckoutStore.persist.getOptions().merge!;
    const merged = merge(
      { shippingZoneCode: 42, shippingAddressId: { id: 'x' }, discountCode: null },
      useCheckoutStore.getState()
    ) as unknown as Record<string, unknown>;

    expect(merged.shippingZoneCode).toBe('');
    expect(merged.shippingAddressId).toBe('');
    expect(merged.discountCode).toBe('');
  });

  it('survives a completely absent persisted state', () => {
    const merge = useCheckoutStore.persist.getOptions().merge!;
    const merged = merge(undefined, useCheckoutStore.getState()) as { step: string };
    expect(merged.step).toBe('shipping');
  });
});
