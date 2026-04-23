import { useEffect, useMemo, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { useCartStore } from '../lib/cartStore';
import { useAuthStore, readAuthTokenCookie } from '../lib/authStore';
import { createOrder, getPublicStoreSettings, type PaymentGatewayProvider } from '../lib/api';
import type { Locale } from '../i18n/index';

interface Props {
  locale: Locale;
}

const labels = {
  es: {
    title: 'Carrito',
    empty: 'Tu carrito está vacío',
    emptyLink: 'Explorar productos',
    subtotal: 'Subtotal',
    workerDiscount: 'Descuento trabajador (10%)',
    total: 'Total',
    checkout: 'Finalizar Compra',
    checkoutLoading: 'Procesando...',
    paymentMethod: 'Metodo de pago',
    paymentMethodTransfer: 'Transferencia',
    paymentMethodGateway: 'Pasarela de pago',
    paymentMethodUnavailable: 'El metodo de pago seleccionado ya no esta disponible.',
    paymentProviderLabel: 'Proveedor',
    transferDetailsTitle: 'Datos para transferencia',
    transferHolder: 'Nombre',
    transferEmail: 'Correo',
    transferAccount: 'Numero de cuenta',
    transferType: 'Tipo de cuenta',
    remove: 'Eliminar',
    quantity: 'Cantidad',
    checkoutError: 'No pudimos crear tu pedido. Inténtalo nuevamente.',
    checkoutSuccess: 'Pedido creado. Redirigiendo a tu cuenta...',
    continueShopping: 'Seguir comprando',
  },
  en: {
    title: 'Cart',
    empty: 'Your cart is empty',
    emptyLink: 'Explore products',
    subtotal: 'Subtotal',
    workerDiscount: 'Employee discount (10%)',
    total: 'Total',
    checkout: 'Checkout',
    checkoutLoading: 'Processing...',
    paymentMethod: 'Payment method',
    paymentMethodTransfer: 'Bank transfer',
    paymentMethodGateway: 'Payment gateway',
    paymentMethodUnavailable: 'The selected payment method is no longer available.',
    paymentProviderLabel: 'Provider',
    transferDetailsTitle: 'Bank transfer details',
    transferHolder: 'Name',
    transferEmail: 'Email',
    transferAccount: 'Account number',
    transferType: 'Account type',
    remove: 'Remove',
    quantity: 'Quantity',
    checkoutError: 'We could not create your order. Please try again.',
    checkoutSuccess: 'Order created. Redirecting to your account...',
    continueShopping: 'Continue shopping',
  },
} as const;

export default function CartPage({ locale }: Props) {
  const l = labels[locale];
  const items = useCartStore((s) => s.items);
  const removeItem = useCartStore((s) => s.removeItem);
  const updateQuantity = useCartStore((s) => s.updateQuantity);
  const clearCart = useCartStore((s) => s.clearCart);

  const authUser = useAuthStore((s) => s.user);
  const authToken = useAuthStore((s) => s.token);
  const effectiveToken = authToken ?? readAuthTokenCookie();

  const [toast, setToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [checkingOut, setCheckingOut] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<'BANK_TRANSFER' | 'PAYMENT_GATEWAY'>('BANK_TRANSFER');
  const [paymentMethodBankTransferEnabled, setPaymentMethodBankTransferEnabled] = useState(true);
  const [paymentMethodGatewayEnabled, setPaymentMethodGatewayEnabled] = useState(true);
  const [paymentGatewayProviders, setPaymentGatewayProviders] = useState<PaymentGatewayProvider[]>(['MERCADO_PAGO']);
  const [transferAccountHolder, setTransferAccountHolder] = useState('');
  const [transferContactEmail, setTransferContactEmail] = useState('');
  const [transferAccountNumber, setTransferAccountNumber] = useState('');
  const [transferAccountType, setTransferAccountType] = useState('');

  const subtotal = items.reduce((sum, i) => sum + i.price.amount * i.quantity, 0);
  const isEmployee = authUser?.role === 'SELLER';
  const employeeDiscountAmount = isEmployee ? Math.round(subtotal * 0.1) : 0;
  const total = Math.max(0, subtotal - employeeDiscountAmount);

  const priceFormat = (amount: number, currency: string) =>
    new Intl.NumberFormat(locale === 'es' ? 'es-CL' : 'en-US', {
      style: 'currency',
      currency,
      maximumFractionDigits: 0,
    }).format(amount);

  function showToast(type: 'success' | 'error', message: string) {
    setToast({ type, message });
    window.setTimeout(() => setToast(null), 3200);
  }

  useEffect(() => {
    let cancelled = false;
    async function loadPaymentConfig() {
      try {
        const settings = await getPublicStoreSettings();
        if (!settings || cancelled) return;

        const providers = Array.isArray(settings.paymentGatewayProviders)
          ? settings.paymentGatewayProviders
          : [];
        const gatewayEnabled = settings.paymentMethodGatewayEnabled !== false && providers.length > 0;
        const bankTransferEnabled = settings.paymentMethodBankTransferEnabled !== false || !gatewayEnabled;

        setPaymentGatewayProviders(providers.length ? providers : ['MERCADO_PAGO']);
        setPaymentMethodGatewayEnabled(gatewayEnabled);
        setPaymentMethodBankTransferEnabled(bankTransferEnabled);
        setTransferAccountHolder(settings.bankTransferAccountHolder?.trim() ?? '');
        setTransferContactEmail(settings.bankTransferContactEmail?.trim() ?? '');
        setTransferAccountNumber(settings.bankTransferAccountNumber?.trim() ?? '');
        setTransferAccountType(settings.bankTransferAccountType?.trim() ?? '');

        setPaymentMethod((prev) => {
          if (prev === 'BANK_TRANSFER' && !bankTransferEnabled && gatewayEnabled) {
            return 'PAYMENT_GATEWAY';
          }
          if (prev === 'PAYMENT_GATEWAY' && !gatewayEnabled && bankTransferEnabled) {
            return 'BANK_TRANSFER';
          }
          return prev;
        });
      } catch {
        // Keep defaults if public settings are unavailable.
      }
    }

    void loadPaymentConfig();
    return () => {
      cancelled = true;
    };
  }, []);

  const selectedGatewayProviderLabel = useMemo(() => {
    if (!paymentGatewayProviders.length) return '';
    const first = paymentGatewayProviders[0];
    if (first === 'MERCADO_PAGO') {
      return 'Mercado Pago';
    }
    return first;
  }, [paymentGatewayProviders]);

  async function handleCheckout() {
    if (!items.length || checkingOut) return;

    if (paymentMethod === 'BANK_TRANSFER' && !paymentMethodBankTransferEnabled) {
      showToast('error', l.paymentMethodUnavailable);
      return;
    }
    if (paymentMethod === 'PAYMENT_GATEWAY' && !paymentMethodGatewayEnabled) {
      showToast('error', l.paymentMethodUnavailable);
      return;
    }

    if (!authUser || !effectiveToken) {
      window.location.href = `/${locale}/auth/login?redirect=/${locale}/cart`;
      return;
    }

    setCheckingOut(true);
    setToast(null);
    try {
      const order = await createOrder(
        {
          customerId: authUser.id,
          items: items.map((item) => ({ productId: item.productId ?? item.id, quantity: item.quantity })),
          paymentMethod,
        },
        effectiveToken
      );

      clearCart();
      showToast('success', l.checkoutSuccess);
      window.setTimeout(() => {
        window.location.href = `/${locale}/account?tab=orders&order=${encodeURIComponent(order.id)}`;
      }, 500);
    } catch {
      showToast('error', l.checkoutError);
    } finally {
      setCheckingOut(false);
    }
  }

  return (
    <div className="py-12 bg-pe-beige min-h-screen">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
        <h1 className="font-display text-pe-black text-3xl md:text-4xl font-semibold mb-8">
          {l.title}
        </h1>

        {toast && (
          <div
            role="status"
            aria-live="polite"
            className={`fixed bottom-6 right-6 z-[70] font-sans text-sm px-5 py-3 shadow-xl border max-w-[calc(100vw-2rem)] ${
              toast.type === 'success'
                ? 'bg-pe-black text-pe-white border-pe-white/15'
                : 'bg-[#5f1e25] text-pe-white border-[#f1c3cb]/40'
            }`}
          >
            {toast.message}
          </div>
        )}

        {items.length === 0 ? (
          <div className="text-center py-24">
            <p className="font-display text-pe-charcoal/65 text-2xl mb-6">{l.empty}</p>
            <a
              href={`/${locale}/products`}
              className="font-sans text-sm tracking-widest uppercase text-pe-gold hover:underline underline-offset-4"
            >
              {l.emptyLink} →
            </a>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            <div className="lg:col-span-2 flex flex-col gap-4">
              {items.map((item) => (
                <div key={item.id} className="bg-pe-white flex gap-4 p-4">
                  <img
                    src={item.imageUrl}
                    alt={item.name}
                    className="w-20 h-24 object-cover flex-shrink-0"
                    width="80"
                    height="96"
                  />
                  <div className="flex-1 min-w-0 flex flex-col gap-1">
                    <span className="font-sans text-[10px] tracking-[0.3em] uppercase text-pe-gold">
                      {item.brand}
                    </span>
                    <p className="font-display text-pe-black text-sm font-semibold truncate">
                      {item.name}
                    </p>
                    {item.variantLabel && (
                      <p className="font-sans text-[11px] tracking-wide text-pe-charcoal/55">
                        {item.variantLabel}
                      </p>
                    )}
                    <p className="font-sans text-pe-black text-sm">
                      {priceFormat(item.price.amount, item.price.currency)}
                    </p>

                    <div className="mt-auto flex items-center justify-between gap-4">
                      <div className="flex items-center gap-2" aria-label={l.quantity}>
                        <button
                          onClick={() => updateQuantity(item.id, item.quantity - 1)}
                          className="w-7 h-7 border border-pe-black/20 flex items-center justify-center font-sans text-sm hover:border-pe-gold hover:text-pe-gold transition-colors"
                          aria-label="Decrease quantity"
                        >
                          -
                        </button>
                        <span className="font-sans text-sm w-5 text-center">{item.quantity}</span>
                        <button
                          onClick={() => updateQuantity(item.id, item.quantity + 1)}
                          className="w-7 h-7 border border-pe-black/20 flex items-center justify-center font-sans text-sm hover:border-pe-gold hover:text-pe-gold transition-colors"
                          aria-label="Increase quantity"
                        >
                          +
                        </button>
                      </div>

                      <span className="font-sans text-sm font-semibold">
                        {priceFormat(item.price.amount * item.quantity, item.price.currency)}
                      </span>

                      <button
                        onClick={() => removeItem(item.id)}
                        className="font-sans text-[10px] tracking-widest uppercase text-pe-charcoal/65 hover:text-pe-gold transition-colors"
                        aria-label={`${l.remove} ${item.name}`}
                      >
                        {l.remove}
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            <div className="lg:col-span-1">
              <div className="bg-pe-white p-6 sticky top-24">
                <h2 className="font-display text-pe-black text-xl font-semibold mb-6">
                  {locale === 'es' ? 'Resumen' : 'Summary'}
                </h2>

                <div className="flex justify-between font-sans text-sm mb-4">
                  <span className="text-pe-charcoal/75">{l.subtotal}</span>
                  <span className="font-semibold">
                    {priceFormat(subtotal, items[0]?.price.currency ?? 'CLP')}
                  </span>
                </div>

                {isEmployee && (
                  <div className="flex justify-between font-sans text-sm mb-4">
                    <span className="text-pe-charcoal/75">{l.workerDiscount}</span>
                    <span className="font-semibold text-green-700">
                      - {priceFormat(employeeDiscountAmount, items[0]?.price.currency ?? 'CLP')}
                    </span>
                  </div>
                )}

                <div className="w-full h-px bg-pe-black/10 mb-6"></div>

                <div className="mb-6">
                  <p className="font-sans text-[0.68rem] tracking-[0.16em] uppercase text-pe-charcoal/75 mb-2">
                    {l.paymentMethod}
                  </p>
                  <div className="flex flex-col gap-2">
                    {paymentMethodBankTransferEnabled && (
                      <label className="inline-flex items-center gap-2 font-sans text-sm text-pe-charcoal">
                        <input
                          type="radio"
                          name="paymentMethod"
                          value="BANK_TRANSFER"
                          checked={paymentMethod === 'BANK_TRANSFER'}
                          onChange={() => setPaymentMethod('BANK_TRANSFER')}
                          className="accent-pe-rose"
                        />
                        {l.paymentMethodTransfer}
                      </label>
                    )}
                    {paymentMethodGatewayEnabled && (
                      <label className="inline-flex items-start gap-2 font-sans text-sm text-pe-charcoal">
                        <input
                          type="radio"
                          name="paymentMethod"
                          value="PAYMENT_GATEWAY"
                          checked={paymentMethod === 'PAYMENT_GATEWAY'}
                          onChange={() => setPaymentMethod('PAYMENT_GATEWAY')}
                          className="mt-1 accent-pe-rose"
                        />
                        <span>
                          {l.paymentMethodGateway}
                          {selectedGatewayProviderLabel && (
                            <span className="block text-[0.68rem] text-pe-charcoal/60">
                              {l.paymentProviderLabel}: {selectedGatewayProviderLabel}
                            </span>
                          )}
                        </span>
                      </label>
                    )}
                  </div>
                  {paymentMethod === 'BANK_TRANSFER' && (
                    <div className="mt-3 border border-pe-black/10 bg-pe-cream/35 px-3 py-2">
                      <p className="font-sans text-[0.65rem] uppercase tracking-[0.16em] text-pe-charcoal/55 mb-2">
                        {l.transferDetailsTitle}
                      </p>
                      <dl className="grid grid-cols-1 gap-1.5 font-sans text-[0.74rem] text-pe-charcoal/75">
                        <div className="flex items-center justify-between gap-3">
                          <dt className="text-pe-charcoal/55">{l.transferHolder}</dt>
                          <dd className="text-right">{transferAccountHolder || '-'}</dd>
                        </div>
                        <div className="flex items-center justify-between gap-3">
                          <dt className="text-pe-charcoal/55">{l.transferEmail}</dt>
                          <dd className="text-right">{transferContactEmail || '-'}</dd>
                        </div>
                        <div className="flex items-center justify-between gap-3">
                          <dt className="text-pe-charcoal/55">{l.transferAccount}</dt>
                          <dd className="text-right">{transferAccountNumber || '-'}</dd>
                        </div>
                        <div className="flex items-center justify-between gap-3">
                          <dt className="text-pe-charcoal/55">{l.transferType}</dt>
                          <dd className="text-right">{transferAccountType || '-'}</dd>
                        </div>
                      </dl>
                    </div>
                  )}
                </div>

                <div className="flex justify-between font-sans text-sm mb-6">
                  <span className="text-pe-charcoal">{l.total}</span>
                  <span className="font-semibold">
                    {priceFormat(total, items[0]?.price.currency ?? 'CLP')}
                  </span>
                </div>

                <button
                  onClick={handleCheckout}
                  disabled={checkingOut}
                  className="w-full bg-pe-gold text-pe-black font-sans text-xs tracking-widest uppercase py-3 hover:bg-opacity-90 active:scale-95 transition-all duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-pe-gold disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {checkingOut ? (
                    <span className="inline-flex items-center justify-center gap-2">
                      <Loader2 size={13} className="animate-spin" />
                      {l.checkoutLoading}
                    </span>
                  ) : (
                    l.checkout
                  )}
                </button>

                <a
                  href={`/${locale}/products`}
                  className="block text-center font-sans text-[10px] tracking-widest uppercase text-pe-charcoal/65 hover:text-pe-gold transition-colors mt-4"
                >
                  {l.continueShopping}
                </a>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

