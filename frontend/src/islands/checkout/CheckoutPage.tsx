import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, ArrowLeft, Loader2 } from 'lucide-react';
import { useCartStore } from '../../lib/cartStore';
import { readAuthTokenCookie, useAuthStore } from '../../lib/authStore';
import {
  useCheckoutStore,
  slugToStep,
  stepIndex,
  stepToSlug,
  type CheckoutStep,
} from '../../lib/checkoutStore';
import { computeTotals } from '../../lib/checkoutTotals';
import { useStockCheck } from '../../lib/useStockCheck';
import {
  createOrder,
  getMyAddresses,
  validateDiscountCodeForUser,
  type CustomerAddressDto,
  type DiscountCodeDto,
} from '../../lib/api';
import { useCheckoutConfig } from './useCheckoutConfig';
import StepIndicator from './StepIndicator';
import OrderSummary from './OrderSummary';
import ShippingStep from './steps/ShippingStep';
import PaymentStep from './steps/PaymentStep';
import ReviewStep from './steps/ReviewStep';
import type { Locale } from '../../i18n/index';

interface Props {
  readonly locale: Locale;
}

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Cart items persisted by older builds carry a composite id (`<uuid>::<variant>`) and no
 * `productId`. Returns null for anything that is not a real product id, so the order is
 * refused here rather than by the backend with an opaque message.
 */
function resolveProductId(item: { id: string; productId?: string }): string | null {
  const candidate = item.productId?.trim() || item.id.split('::')[0]?.trim() || '';
  return UUID_REGEX.test(candidate) ? candidate : null;
}

const copy = {
  es: {
    title: 'Finalizar compra',
    backToCart: 'Volver al carrito',
    loading: 'Cargando…',
    emptyCart: 'Tu carrito está vacío.',
    emptyCartAction: 'Explorar productos',
    unavailable:
      'No pudimos cargar la configuración de la tienda. Vuelve a intentarlo en unos minutos.',
    discountLoginRequired: 'Debes iniciar sesión para usar un código.',
    discountInvalid: 'Código inválido o ya utilizado.',
    missingSelection: 'Falta información de envío. Vuelve al primer paso.',
    legacyItem: 'Hay un producto antiguo en tu carrito. Quítalo y vuelve a agregarlo.',
    submitFailed: 'No pudimos crear el pedido. Inténtalo de nuevo.',
    stockChanged: 'La disponibilidad cambió mientras completabas la compra. Revisa los productos marcados.',
    stockRejected: 'Uno de los productos ya no está disponible en la cantidad o talla elegida. Revisa tu pedido.',
  },
  en: {
    title: 'Checkout',
    backToCart: 'Back to cart',
    loading: 'Loading…',
    emptyCart: 'Your cart is empty.',
    emptyCartAction: 'Browse products',
    unavailable: 'We could not load the store configuration. Please try again in a few minutes.',
    discountLoginRequired: 'You must be logged in to apply a code.',
    discountInvalid: 'Invalid or already used code.',
    missingSelection: 'Shipping information is missing. Go back to the first step.',
    legacyItem: 'Your cart holds a legacy item. Remove it and add it again.',
    submitFailed: 'We could not create the order. Please try again.',
    stockChanged: 'Availability changed while you were checking out. Review the flagged items.',
    stockRejected: 'One of the items is no longer available in the size or quantity chosen. Review your order.',
  },
} as const;

export default function CheckoutPage({ locale }: Props) {
  const l = copy[locale === 'es' ? 'es' : 'en'];

  const items = useCartStore((s) => s.items);
  const authUser = useAuthStore((s) => s.user);
  const storeToken = useAuthStore((s) => s.token);
  const [cookieToken, setCookieToken] = useState<string | null>(null);
  useEffect(() => setCookieToken(readAuthTokenCookie()), []);
  const token = storeToken ?? cookieToken;

  const step = useCheckoutStore((s) => s.step);
  const furthestStep = useCheckoutStore((s) => s.furthestStep);
  const setStep = useCheckoutStore((s) => s.setStep);
  const discountCode = useCheckoutStore((s) => s.discountCode);
  const setDiscountCode = useCheckoutStore((s) => s.setDiscountCode);
  const completeStep = useCheckoutStore((s) => s.completeStep);
  const shippingZoneCode = useCheckoutStore((s) => s.shippingZoneCode);
  const shippingCourierId = useCheckoutStore((s) => s.shippingCourierId);
  const shippingAddressId = useCheckoutStore((s) => s.shippingAddressId);
  const setShipping = useCheckoutStore((s) => s.setShipping);
  const paymentMethod = useCheckoutStore((s) => s.paymentMethod);
  const setPaymentMethod = useCheckoutStore((s) => s.setPaymentMethod);
  const resetCheckout = useCheckoutStore((s) => s.reset);
  const clearCart = useCartStore((s) => s.clearCart);
  const removeItem = useCartStore((s) => s.removeItem);

  const config = useCheckoutConfig();
  const stock = useStockCheck(items);

  const [addresses, setAddresses] = useState<CustomerAddressDto[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [appliedDiscount, setAppliedDiscount] = useState<DiscountCodeDto | null>(null);
  const [discountApplying, setDiscountApplying] = useState(false);
  const [discountError, setDiscountError] = useState('');

  const subtotal = useMemo(
    () => items.reduce((sum, i) => sum + i.price.amount * i.quantity, 0),
    [items]
  );
  const currency = items[0]?.price.currency ?? 'CLP';
  const totals = useMemo(
    () => computeTotals(subtotal, { isEmployee: authUser?.role === 'SELLER', discount: appliedDiscount }),
    [subtotal, authUser?.role, appliedDiscount]
  );

  /**
   * Reads the step out of the URL so a shared link and the browser back button both work — but
   * never past the furthest step actually reached.
   *
   * <p>Without the clamp, ?paso=resumen dropped anybody straight onto the review screen with no
   * address, no courier and no zone chosen, and the only thing that noticed was the Pagar button,
   * which failed at the last possible moment and told them to go back to the beginning. Going back
   * to an earlier step stays free: revisiting what you already answered is not skipping.
   */
  useEffect(() => {
    const enter = (target: CheckoutStep | null) => {
      if (!target) return;
      setStep(stepIndex(target) > stepIndex(furthestStep) ? furthestStep : target);
    };

    enter(slugToStep(new URLSearchParams(window.location.search).get('paso')));

    const onPopState = () => {
      enter(slugToStep(new URLSearchParams(window.location.search).get('paso')));
    };
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, [setStep, furthestStep]);

  /** Keeps the URL in step with the machine without stacking a history entry per render. */
  useEffect(() => {
    const url = new URL(window.location.href);
    if (url.searchParams.get('paso') === stepToSlug(step)) return;
    url.searchParams.set('paso', stepToSlug(step));
    window.history.pushState({}, '', url);
  }, [step]);

  /**
   * Re-checks the stored code against the server on mount. The code survives a login
   * redirect in localStorage but its validity does not: ownership, prior use and the usage
   * cap can all have changed, and only the backend knows.
   */
  const revalidatedFor = useRef<string | null>(null);
  useEffect(() => {
    if (!discountCode || !token || subtotal <= 0) return;
    const key = `${discountCode}:${subtotal}`;
    if (revalidatedFor.current === key) return;
    revalidatedFor.current = key;

    let cancelled = false;
    void (async () => {
      try {
        const dto = await validateDiscountCodeForUser(discountCode, subtotal, token);
        if (!cancelled) setAppliedDiscount(dto);
      } catch {
        if (cancelled) return;
        /* Drop it silently: the customer did not just type it, so an error here is noise. */
        setAppliedDiscount(null);
        setDiscountCode('');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [discountCode, token, subtotal, setDiscountCode]);

  const applyDiscount = useCallback(
    async (code: string) => {
      if (!token) {
        setDiscountError(l.discountLoginRequired);
        return;
      }
      setDiscountApplying(true);
      setDiscountError('');
      try {
        const dto = await validateDiscountCodeForUser(code, subtotal, token);
        setAppliedDiscount(dto);
        setDiscountCode(dto.code);
        revalidatedFor.current = `${dto.code}:${subtotal}`;
      } catch (e) {
        setAppliedDiscount(null);
        setDiscountError(e instanceof Error && e.message ? e.message : l.discountInvalid);
      } finally {
        setDiscountApplying(false);
      }
    },
    [token, subtotal, setDiscountCode, l.discountLoginRequired, l.discountInvalid]
  );

  const removeDiscount = useCallback(() => {
    setAppliedDiscount(null);
    setDiscountCode('');
    setDiscountError('');
    revalidatedFor.current = null;
  }, [setDiscountCode]);

  const selectStep = useCallback((next: CheckoutStep) => setStep(next), [setStep]);

  const selectedAddress = useMemo(
    () => addresses.find((a) => a.id === shippingAddressId) ?? null,
    [addresses, shippingAddressId]
  );
  const selectedCourierName =
    config.couriers.find((c) => c.id === shippingCourierId)?.name ?? '';
  const selectedZone = config.zones.find((z) => z.code === shippingZoneCode);
  let selectedZoneName = '';
  let selectedZoneEta = '';
  if (selectedZone) {
    selectedZoneName = locale === 'es' ? selectedZone.titleEs : selectedZone.titleEn;
    selectedZoneEta = locale === 'es' ? selectedZone.etaEs : selectedZone.etaEn;
  }

  /** The review step needs the chosen address by value; the shipping step loads the list. */
  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    void (async () => {
      try {
        const rows = await getMyAddresses(token);
        if (!cancelled) setAddresses(rows);
      } catch {
        /* The shipping step reports this; failing here would double the message. */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [token]);

  async function placeOrder() {
    if (submitting) return;
    /*
     * Resolved against the loaded config rather than trusted from the store: it also proves
     * the zone is still one the admin has active, and yields the narrow type createOrder wants.
     */
    if (!authUser || !token || !shippingAddressId || !selectedZone || !shippingCourierId) {
      setSubmitError(l.missingSelection);
      return;
    }

    const productIds = items.map((item) => resolveProductId(item));
    if (productIds.includes(null)) {
      setSubmitError(l.legacyItem);
      return;
    }

    setSubmitting(true);
    setSubmitError('');
    try {
      /*
       * Last check before the order exists. Stock can go in the minutes a customer spends on
       * these steps, and the backend refusing at this point is the least useful moment to find
       * out: the message is generic and the cart is still full of the offending line. Checking
       * here lets the review step name the item and offer to drop it.
       */
      const found = await stock.check();
      if (Object.keys(found).length > 0) {
        setSubmitError(l.stockChanged);
        setSubmitting(false);
        return;
      }

      const order = await createOrder(
        {
          customerId: authUser.id,
          items: items.map((item, index) => ({
            productId: productIds[index] as string,
            quantity: item.quantity,
            variantColor: item.variantColor,
            variantSize: item.variantSize,
          })),
          paymentMethod,
          shippingZoneCode: selectedZone.code,
          shippingCourierId,
          shippingAddressId,
          discountCode: appliedDiscount?.code,
        },
        token
      );

      /*
       * Clear both stores only once the order exists. Doing it optimistically would lose the
       * cart on any failure, and the customer would have to rebuild it to retry.
       */
      clearCart();
      resetCheckout();
      window.location.href = `/${locale}/account?tab=orders&order=${encodeURIComponent(order.id)}`;
    } catch (error) {
      const raw = error instanceof Error ? error.message : '';

      /*
       * The backend's own words reach the customer when they are written for one — a rejected
       * discount code says exactly what is wrong. Its inventory refusals do not: they surface as
       * "Inventory reservation rejected (status 400)", untranslated and unactionable. Those get
       * replaced, and the offending lines re-checked so the review step can name them.
       */
      if (/inventory|stock|variant/i.test(raw)) {
        await stock.check();
        setSubmitError(l.stockRejected);
      } else {
        setSubmitError(raw || l.submitFailed);
      }
      setSubmitting(false);
    }
  }

  if (items.length === 0) {
    return (
      <div className="py-20 bg-pe-beige min-h-screen">
        <div className="max-w-2xl mx-auto px-4 text-center">
          <p className="font-sans text-pe-charcoal mb-6">{l.emptyCart}</p>
          <a
            href={`/${locale}/products`}
            className="inline-flex items-center min-h-11 px-6 bg-pe-black text-pe-white
              font-sans text-[0.7rem] tracking-[0.16em] uppercase"
          >
            {l.emptyCartAction}
          </a>
        </div>
      </div>
    );
  }

  let mainContent: React.ReactNode;
  if (config.loading) {
    mainContent = (
      <div className="bg-pe-white p-12 flex items-center justify-center gap-3">
        <Loader2 size={18} className="animate-spin text-pe-muted" />
        <span className="font-sans text-sm text-pe-charcoal">{l.loading}</span>
      </div>
    );
  } else if (config.unavailable) {
    mainContent = (
      <div
        role="alert"
        className="bg-pe-white p-6 flex items-start gap-3 border-l-2 border-[#cb6070]"
      >
        <AlertTriangle size={18} className="text-[#8f2d3b] shrink-0 mt-0.5" />
        <p className="font-sans text-sm text-pe-charcoal">{l.unavailable}</p>
      </div>
    );
  } else {
    mainContent = (
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          {step === 'shipping' && (
            <ShippingStep
              locale={locale}
              token={token}
              zones={config.zones}
              couriers={config.couriers}
              zoneCode={shippingZoneCode}
              courierId={shippingCourierId}
              addressId={shippingAddressId}
              onChange={setShipping}
              onContinue={() => completeStep('shipping')}
            />
          )}

          {step === 'payment' && (
            <PaymentStep
              locale={locale}
              method={paymentMethod}
              transferEnabled={config.bankTransferEnabled}
              gatewayEnabled={config.gatewayEnabled}
              gatewayLabel={config.gatewayLabel}
              transfer={config.transfer}
              transferWindowMinutes={config.transferWindowMinutes}
              onSelect={setPaymentMethod}
              onBack={() => setStep('shipping')}
              onContinue={() => completeStep('payment')}
            />
          )}

          {step === 'review' && (
            <ReviewStep
              locale={locale}
              items={items}
              address={selectedAddress}
              method={paymentMethod}
              courierName={selectedCourierName}
              zoneName={selectedZoneName}
              shippingEta={selectedZoneEta}
              total={totals.total}
              currency={currency}
              submitting={submitting}
              error={submitError}
              stockIssues={stock.issues}
              onRemoveItem={(lineId) => {
                stock.clearIssue(lineId);
                removeItem(lineId);
              }}
              onBack={() => setStep('payment')}
              onFixShipping={() => setStep('shipping')}
              onSubmit={placeOrder}
            />
          )}
        </div>
        <div className="lg:col-span-1">
          <OrderSummary
            locale={locale}
            currency={currency}
            totals={totals}
            appliedDiscount={appliedDiscount}
            applying={discountApplying}
            error={discountError}
            onApply={applyDiscount}
            onRemove={removeDiscount}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="py-8 sm:py-12 bg-pe-beige min-h-screen">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
        <a
          href={`/${locale}/cart`}
          className="inline-flex items-center gap-2 min-h-11 font-sans text-[0.7rem]
            tracking-[0.16em] uppercase text-pe-charcoal hover:text-pe-black transition-colors"
        >
          <ArrowLeft size={14} />
          {l.backToCart}
        </a>

        <h1 className="font-display text-pe-black text-3xl md:text-4xl font-semibold mt-2 mb-8">
          {l.title}
        </h1>

        <div className="bg-pe-white mb-6 px-2 sm:px-4">
          <StepIndicator
            locale={locale}
            current={step}
            furthest={furthestStep}
            onSelect={selectStep}
          />
        </div>

        {mainContent}
      </div>
    </div>
  );
}
