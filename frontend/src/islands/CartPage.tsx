import { useState } from 'react';
import { useCartStore } from '../lib/cartStore';
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
    checkout: 'Finalizar Compra',
    remove: 'Eliminar',
    quantity: 'Cantidad',
    soon: 'Próximamente disponible',
    continueShopping: 'Seguir comprando',
  },
  en: {
    title: 'Cart',
    empty: 'Your cart is empty',
    emptyLink: 'Explore products',
    subtotal: 'Subtotal',
    checkout: 'Checkout',
    remove: 'Remove',
    quantity: 'Quantity',
    soon: 'Coming soon',
    continueShopping: 'Continue shopping',
  },
} as const;

export default function CartPage({ locale }: Props) {
  const l = labels[locale];
  const items = useCartStore((s) => s.items);
  const removeItem = useCartStore((s) => s.removeItem);
  const updateQuantity = useCartStore((s) => s.updateQuantity);
  const [toast, setToast] = useState(false);

  const subtotal = items.reduce((sum, i) => sum + i.price.amount * i.quantity, 0);

  const priceFormat = (amount: number, currency: string) =>
    new Intl.NumberFormat('es-CL', {
      style: 'currency',
      currency,
      maximumFractionDigits: 0,
    }).format(amount);

  function handleCheckout() {
    setToast(true);
    setTimeout(() => setToast(false), 3000);
  }

  return (
    <div className="py-12 bg-pe-beige min-h-screen">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
        <h1 className="font-display text-pe-black text-3xl md:text-4xl font-semibold mb-8">
          {l.title}
        </h1>

        {/* Toast */}
        {toast && (
          <div
            role="status"
            aria-live="polite"
            className="fixed bottom-6 right-6 z-[70] bg-pe-black text-pe-white font-sans text-sm px-5 py-3 shadow-xl border border-pe-white/15 max-w-[calc(100vw-2rem)]"
          >
            {l.soon}
          </div>
        )}

        {items.length === 0 ? (
          <div className="text-center py-24">
            <p className="font-display text-pe-black/30 text-2xl mb-6">{l.empty}</p>
            <a
              href={`/${locale}/products`}
              className="font-sans text-sm tracking-widest uppercase text-pe-gold hover:underline underline-offset-4"
            >
              {l.emptyLink} →
            </a>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            {/* Line items */}
            <div className="lg:col-span-2 flex flex-col gap-4">
              {items.map((item) => (
                <div
                  key={item.id}
                  className="bg-pe-white flex gap-4 p-4"
                >
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
                    <p className="font-sans text-pe-black text-sm">
                      {priceFormat(item.price.amount, item.price.currency)}
                    </p>

                    <div className="mt-auto flex items-center justify-between gap-4">
                      {/* Quantity controls */}
                      <div className="flex items-center gap-2" aria-label={l.quantity}>
                        <button
                          onClick={() => updateQuantity(item.id, item.quantity - 1)}
                          className="w-7 h-7 border border-pe-black/20 flex items-center justify-center font-sans text-sm hover:border-pe-gold hover:text-pe-gold transition-colors"
                          aria-label="Decrease quantity"
                        >
                          −
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

                      {/* Line total */}
                      <span className="font-sans text-sm font-semibold">
                        {priceFormat(item.price.amount * item.quantity, item.price.currency)}
                      </span>

                      {/* Remove */}
                      <button
                        onClick={() => removeItem(item.id)}
                        className="font-sans text-[10px] tracking-widest uppercase text-pe-black/30 hover:text-pe-gold transition-colors"
                        aria-label={`${l.remove} ${item.name}`}
                      >
                        {l.remove}
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {/* Order summary */}
            <div className="lg:col-span-1">
              <div className="bg-pe-white p-6 sticky top-24">
                <h2 className="font-display text-pe-black text-xl font-semibold mb-6">
                  {locale === 'es' ? 'Resumen' : 'Summary'}
                </h2>

                <div className="flex justify-between font-sans text-sm mb-4">
                  <span className="text-pe-black/60">{l.subtotal}</span>
                  <span className="font-semibold">
                    {priceFormat(subtotal, items[0]?.price.currency ?? 'CLP')}
                  </span>
                </div>

                <div className="w-full h-px bg-pe-black/10 mb-6"></div>

                <button
                  onClick={handleCheckout}
                  className="w-full bg-pe-gold text-pe-black font-sans text-xs tracking-widest uppercase py-3 hover:bg-opacity-90 active:scale-95 transition-all duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-pe-gold"
                >
                  {l.checkout}
                </button>

                <a
                  href={`/${locale}/products`}
                  className="block text-center font-sans text-[10px] tracking-widest uppercase text-pe-black/40 hover:text-pe-gold transition-colors mt-4"
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
