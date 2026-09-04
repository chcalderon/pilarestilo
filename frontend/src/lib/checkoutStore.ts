import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type CheckoutStep = 'shipping' | 'payment' | 'review';
export type CheckoutPaymentMethod = 'TRANSFER' | 'WEBPAY';

export const CHECKOUT_STEPS: readonly CheckoutStep[] = ['shipping', 'payment', 'review'] as const;

/**
 * The slugs that appear in the URL. The flow is deep-linkable so the browser back
 * button walks the steps instead of leaving checkout entirely.
 */
const STEP_SLUGS: Record<CheckoutStep, string> = {
  shipping: 'envio',
  payment: 'pago',
  review: 'resumen',
};

const SLUG_TO_STEP: Record<string, CheckoutStep> = Object.fromEntries(
  Object.entries(STEP_SLUGS).map(([step, slug]) => [slug, step as CheckoutStep])
) as Record<string, CheckoutStep>;

export function stepToSlug(step: CheckoutStep): string {
  return STEP_SLUGS[step];
}

export function slugToStep(slug: string | null | undefined): CheckoutStep | null {
  if (!slug) return null;
  return SLUG_TO_STEP[slug.toLowerCase()] ?? null;
}

export function stepIndex(step: CheckoutStep): number {
  return CHECKOUT_STEPS.indexOf(step);
}

interface CheckoutState {
  step: CheckoutStep;
  /**
   * The furthest step the customer has actually completed. It gates forward jumps
   * from the step indicator: going back to review an answer is free, skipping ahead
   * past an unanswered step is not.
   */
  furthestStep: CheckoutStep;

  shippingZoneCode: string;
  shippingCourierId: string;
  shippingAddressId: string;
  paymentMethod: CheckoutPaymentMethod;
  /**
   * Only the raw code is kept. The validated discount is deliberately not persisted:
   * it has to be re-checked against the server on every mount, because ownership,
   * prior use and the usage cap can all change between visits.
   */
  discountCode: string;
  /**
   * Minted once per checkout attempt and sent with the order. Dedupes a resubmit -- a refresh
   * mid-request, or a fast double-click racing the submit button's disabled state -- which used
   * to create two real orders (double stock reservation, double discount redemption). Persists
   * through a refresh like the rest of this store; `reset()` mints a fresh one for the next
   * attempt so a completed order is never handed back for an unrelated later purchase.
   */
  idempotencyKey: string;

  setStep: (step: CheckoutStep) => void;
  /** Advances only if `step` is the current one, so a stale click cannot skip ahead. */
  completeStep: (step: CheckoutStep) => void;
  setShipping: (value: { zoneCode?: string; courierId?: string; addressId?: string }) => void;
  setPaymentMethod: (method: CheckoutPaymentMethod) => void;
  setDiscountCode: (code: string) => void;
  reset: () => void;
}

const INITIAL: Pick<
  CheckoutState,
  | 'step'
  | 'furthestStep'
  | 'shippingZoneCode'
  | 'shippingCourierId'
  | 'shippingAddressId'
  | 'paymentMethod'
  | 'discountCode'
> = {
  step: 'shipping',
  furthestStep: 'shipping',
  shippingZoneCode: '',
  shippingCourierId: '',
  shippingAddressId: '',
  paymentMethod: 'TRANSFER',
  discountCode: '',
};

function sanitizeStep(value: unknown): CheckoutStep {
  return CHECKOUT_STEPS.includes(value as CheckoutStep) ? (value as CheckoutStep) : 'shipping';
}

function sanitizeMethod(value: unknown): CheckoutPaymentMethod {
  return value === 'WEBPAY' ? 'WEBPAY' : 'TRANSFER';
}

function sanitizeText(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

/**
 * `crypto.randomUUID()` needs a secure context (https, or localhost) -- true for every real
 * visitor, but not guaranteed for every test/SSR environment this module might load in.
 */
function generateIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `ck-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function sanitizeIdempotencyKey(value: unknown): string {
  return typeof value === 'string' && value.trim() ? value : generateIdempotencyKey();
}

/** The fields reset() and the store's initializer both start from -- idempotencyKey is minted
 * fresh each call, never shared with a previous (possibly already-used) attempt. */
function initialCheckoutFields(): typeof INITIAL & { idempotencyKey: string } {
  return { ...INITIAL, idempotencyKey: generateIdempotencyKey() };
}

/** Persisted state is user-editable, so every field is re-checked on the way in. */
function sanitizeState(raw: unknown): typeof INITIAL & { idempotencyKey: string } {
  if (!raw || typeof raw !== 'object') return initialCheckoutFields();
  const state = raw as Record<string, unknown>;
  const step = sanitizeStep(state['step']);
  const furthest = sanitizeStep(state['furthestStep']);
  return {
    step,
    /** A furthest marker behind the current step would let the indicator lock the customer out. */
    furthestStep: stepIndex(furthest) >= stepIndex(step) ? furthest : step,
    shippingZoneCode: sanitizeText(state['shippingZoneCode']),
    shippingCourierId: sanitizeText(state['shippingCourierId']),
    shippingAddressId: sanitizeText(state['shippingAddressId']),
    paymentMethod: sanitizeMethod(state['paymentMethod']),
    discountCode: sanitizeText(state['discountCode']),
    idempotencyKey: sanitizeIdempotencyKey(state['idempotencyKey']),
  };
}

export const useCheckoutStore = create<CheckoutState>()(
  persist(
    (set, get) => ({
      ...initialCheckoutFields(),

      setStep: (step) =>
        set((state) => ({
          step,
          furthestStep:
            stepIndex(step) > stepIndex(state.furthestStep) ? step : state.furthestStep,
        })),

      completeStep: (step) => {
        if (get().step !== step) return;
        const next = CHECKOUT_STEPS[stepIndex(step) + 1];
        if (!next) return;
        set((state) => ({
          step: next,
          furthestStep:
            stepIndex(next) > stepIndex(state.furthestStep) ? next : state.furthestStep,
        }));
      },

      setShipping: ({ zoneCode, courierId, addressId }) =>
        set((state) => ({
          shippingZoneCode: zoneCode ?? state.shippingZoneCode,
          shippingCourierId: courierId ?? state.shippingCourierId,
          shippingAddressId: addressId ?? state.shippingAddressId,
        })),

      setPaymentMethod: (paymentMethod) => set({ paymentMethod }),

      setDiscountCode: (discountCode) => set({ discountCode }),

      reset: () => set({ ...initialCheckoutFields() }),
    }),
    {
      name: 'pe-checkout',
      version: 1,
      migrate: (persistedState) => sanitizeState(persistedState),
      merge: (persistedState, currentState) => ({
        ...currentState,
        ...sanitizeState(persistedState),
      }),
    }
  )
);
