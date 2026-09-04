import { beforeEach, describe, expect, it } from 'vitest';
import {
  CHECKOUT_STEPS,
  slugToStep,
  stepIndex,
  stepToSlug,
  useCheckoutStore,
  type CheckoutStep,
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

  /*
   * A refresh mid-request, or a fast double-click racing the submit button's disabled state,
   * used to create two real orders -- this key is what the backend now dedupes on, so it has
   * to actually be there and actually change between unrelated attempts.
   */
  it('gives every checkout attempt a non-empty idempotency key', () => {
    expect(useCheckoutStore.getState().idempotencyKey).toBeTruthy();
  });

  it('reset mints a fresh idempotency key, never reusing a possibly-already-used one', () => {
    const before = useCheckoutStore.getState().idempotencyKey;

    useCheckoutStore.getState().reset();

    expect(useCheckoutStore.getState().idempotencyKey).toBeTruthy();
    expect(useCheckoutStore.getState().idempotencyKey).not.toBe(before);
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

  it('keeps the persisted idempotency key across a refresh, so a retry actually dedupes', () => {
    const merge = useCheckoutStore.persist.getOptions().merge!;
    const merged = merge(
      { idempotencyKey: 'attempt-abc-123' },
      useCheckoutStore.getState()
    ) as { idempotencyKey: string };

    expect(merged.idempotencyKey).toBe('attempt-abc-123');
  });

  it('mints a fresh idempotency key when the persisted one is missing or bogus', () => {
    const merge = useCheckoutStore.persist.getOptions().merge!;

    const missing = merge({}, useCheckoutStore.getState()) as { idempotencyKey: string };
    expect(missing.idempotencyKey).toBeTruthy();

    const bogus = merge({ idempotencyKey: 42 }, useCheckoutStore.getState()) as unknown as {
      idempotencyKey: string;
    };
    expect(bogus.idempotencyKey).toBeTruthy();
    expect(bogus.idempotencyKey).not.toBe(42);
  });

  /*
   * The page reads ?paso= out of the URL and used to obey it without question, so ?paso=resumen
   * landed anybody on the review screen with no address chosen and nothing noticed until the
   * Pagar button failed. These pin the rule the clamp implements: never past furthestStep, and
   * always free to go back.
   */
  describe('the rule that stops a URL skipping ahead', () => {
    const clamp = (target: CheckoutStep, furthest: CheckoutStep): CheckoutStep =>
      stepIndex(target) > stepIndex(furthest) ? furthest : target;

    it('refuses a jump past where the customer actually got to', () => {
      expect(clamp('review', 'shipping')).toBe('shipping');
      expect(clamp('payment', 'shipping')).toBe('shipping');
      expect(clamp('review', 'payment')).toBe('payment');
    });

    it('lets somebody return to a step they already answered', () => {
      expect(clamp('shipping', 'review')).toBe('shipping');
      expect(clamp('payment', 'review')).toBe('payment');
    });

    it('leaves the current step alone', () => {
      expect(clamp('payment', 'payment')).toBe('payment');
    });
  });
});