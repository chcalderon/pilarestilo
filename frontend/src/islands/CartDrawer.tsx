// CartDrawer - slide-over panel for cart (reuses CartPage logic in compact form)
import { useCartStore } from '../lib/cartStore';
import type { Locale } from '../i18n/index';

interface Props {
  locale: Locale;
  isOpen: boolean;
  onClose: () => void;
}

export default function CartDrawer({ locale, isOpen, onClose }: Props) {
  const items = useCartStore((s) => s.items);
  const removeItem = useCartStore((s) => s.removeItem);
  const updateQuantity = useCartStore((s) => s.updateQuantity);

  const subtotal = items.reduce((sum, i) => sum + i.price.amount * i.quantity, 0);
  const priceFormat = (amount: number, currency: string) =>
    new Intl.NumberFormat(locale === 'es' ? 'es-CL' : 'en-US', {
      style: 'currency',
      currency,
      maximumFractionDigits: 0,
    }).format(amount);

  const L = {
    title: locale === 'es' ? 'Carrito' : 'Cart',
    empty: locale === 'es' ? 'Tu carrito está vacío' : 'Your cart is empty',
    subtotal: locale === 'es' ? 'Subtotal' : 'Subtotal',
    checkout: locale === 'es' ? 'Finalizar Compra' : 'Checkout',
    remove: locale === 'es' ? 'Eliminar' : 'Remove',
  };

  if (!isOpen) return null;

  function goToCheckout() {
    onClose();
    window.location.href = `/${locale}/cart`;
  }

  return (
    <>
      <div className="fixed inset-0 bg-pe-black/40 z-40" onClick={onClose} aria-hidden="true" />

      <aside
        className="fixed top-0 right-0 h-full w-full max-w-sm bg-pe-white z-50 flex flex-col shadow-2xl"
        aria-label={L.title}
        role="dialog"
        aria-modal="true"
      >
        <div className="flex items-center justify-between px-6 py-4 border-b border-pe-black/10">
          <h2 className="font-display text-pe-black text-xl font-semibold">{L.title}</h2>
          <button
            onClick={onClose}
            className="text-pe-black/40 hover:text-pe-gold transition-colors"
            aria-label={locale === 'es' ? 'Cerrar carrito' : 'Close cart'}
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-3">
          {items.length === 0 ? (
            <p className="font-sans text-pe-black/40 text-sm text-center mt-8">{L.empty}</p>
          ) : (
            items.map((item) => (
              <div key={item.id} className="flex gap-3">
                <img
                  src={item.imageUrl}
                  alt={item.name}
                  className="w-16 h-20 object-cover flex-shrink-0"
                  width="64"
                  height="80"
                />
                <div className="flex-1 flex flex-col gap-1">
                  <span className="font-sans text-[9px] tracking-[0.3em] uppercase text-pe-gold">{item.brand}</span>
                  <p className="font-display text-pe-black text-xs font-semibold line-clamp-2">{item.name}</p>
                  <p className="font-sans text-pe-black text-xs">{priceFormat(item.price.amount, item.price.currency)}</p>
                  <div className="mt-auto flex items-center justify-between">
                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => updateQuantity(item.id, item.quantity - 1)}
                        className="w-5 h-5 border border-pe-black/20 text-xs flex items-center justify-center hover:border-pe-gold"
                        aria-label={locale === 'es' ? 'Menos' : 'Decrease'}
                      >
                        -
                      </button>
                      <span className="font-sans text-xs w-4 text-center">{item.quantity}</span>
                      <button
                        onClick={() => updateQuantity(item.id, item.quantity + 1)}
                        className="w-5 h-5 border border-pe-black/20 text-xs flex items-center justify-center hover:border-pe-gold"
                        aria-label={locale === 'es' ? 'Más' : 'Increase'}
                      >
                        +
                      </button>
                    </div>
                    <button
                      onClick={() => removeItem(item.id)}
                      className="font-sans text-[9px] uppercase tracking-widest text-pe-black/30 hover:text-pe-gold"
                      aria-label={`${L.remove} ${item.name}`}
                    >
                      {L.remove}
                    </button>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>

        {items.length > 0 && (
          <div className="px-6 py-4 border-t border-pe-black/10">
            <div className="flex justify-between font-sans text-sm mb-4">
              <span className="text-pe-black/60">{L.subtotal}</span>
              <span className="font-semibold">{priceFormat(subtotal, items[0]?.price.currency ?? 'CLP')}</span>
            </div>
            <button
              onClick={goToCheckout}
              className="w-full bg-pe-gold text-pe-black font-sans text-xs tracking-widest uppercase py-2.5 hover:bg-opacity-90 transition-colors"
            >
              {L.checkout}
            </button>
          </div>
        )}
      </aside>
    </>
  );
}
