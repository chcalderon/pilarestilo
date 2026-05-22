import { useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { AnimatePresence, motion } from 'motion/react';
import { ShoppingBag, X } from 'lucide-react';
import { useCartStore } from '../../lib/cartStore';
import type { Locale } from '../../i18n/index';
import CartItemRow from './CartItemRow';
import CartEmptyState from './CartEmptyState';

interface Props {
  locale: Locale;
}

const EASING = [0.22, 0.61, 0.36, 1] as const;

function priceFormat(amount: number, currency = 'CLP', locale: Locale) {
  return new Intl.NumberFormat(locale === 'es' ? 'es-CL' : 'en-US', {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
}

export default function CartPopover({ locale }: Props) {
  const [open, setOpen] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const [mounted, setMounted] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const openTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const items = useCartStore((s) => s.items);
  const removeItem = useCartStore((s) => s.removeItem);
  const subtotal = useCartStore((s) => s.getSubtotal());
  const totalItems = items.reduce((sum, i) => sum + i.quantity, 0);

  const viewCartHref = `/${locale}/cart`;
  const cartLabel = locale === 'es' ? 'Carrito de compras' : 'Shopping cart';

  // SSR safety: only render portals after mount
  useEffect(() => {
    setMounted(true);
  }, []);

  // Detect mobile breakpoint
  useEffect(() => {
    const mq = window.matchMedia('(max-width: 1023px)');
    setIsMobile(mq.matches);
    const handler = (e: MediaQueryListEvent) => setIsMobile(e.matches);
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, []);

  const clearTimers = useCallback(() => {
    if (openTimerRef.current) clearTimeout(openTimerRef.current);
    if (closeTimerRef.current) clearTimeout(closeTimerRef.current);
  }, []);

  const openPopover = useCallback(() => {
    if (closeTimerRef.current) clearTimeout(closeTimerRef.current);
    setOpen(true);
  }, []);

  const closePopover = useCallback(() => {
    if (openTimerRef.current) clearTimeout(openTimerRef.current);
    setOpen(false);
  }, []);

  // Hover intent: open after 200ms delay
  const handleMouseEnter = useCallback(() => {
    if (isMobile) return;
    if (closeTimerRef.current) clearTimeout(closeTimerRef.current);
    openTimerRef.current = setTimeout(openPopover, 200);
  }, [isMobile, openPopover]);

  // Grace period: 300ms before closing on mouse leave
  const handleMouseLeave = useCallback(() => {
    if (isMobile) return;
    if (openTimerRef.current) clearTimeout(openTimerRef.current);
    closeTimerRef.current = setTimeout(closePopover, 300);
  }, [isMobile, closePopover]);

  // Click toggles; cancels any pending hover timers (anchor behavior)
  const handleClick = useCallback(() => {
    clearTimers();
    setOpen((prev) => !prev);
  }, [clearTimers]);

  // Click-outside (desktop only)
  useEffect(() => {
    if (!open || isMobile) return;
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        closePopover();
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open, isMobile, closePopover]);

  // ESC key
  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closePopover();
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [open, closePopover]);

  // Lock body scroll when mobile sheet is open
  useEffect(() => {
    if (!open || !isMobile) return;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = ''; };
  }, [open, isMobile]);

  // Cleanup timers on unmount
  useEffect(() => () => clearTimers(), [clearTimers]);

  const trigger = (
    <button
      type="button"
      onClick={handleClick}
      aria-haspopup="dialog"
      aria-expanded={open}
      aria-controls="cart-popover-content"
      aria-label={cartLabel}
      className="relative flex items-center gap-1 text-pe-white/40 hover:text-pe-rose-soft transition-colors duration-200 p-1"
    >
      <ShoppingBag size={20} strokeWidth={1.5} />
      {totalItems > 0 && (
        <span
          className="inline-flex items-center justify-center w-4 h-4 rounded-full bg-pe-gold text-pe-black text-[10px] font-bold leading-none"
          aria-hidden="true"
        >
          {totalItems > 99 ? '99+' : totalItems}
        </span>
      )}
    </button>
  );

  // Mobile bottom-sheet via portal
  const mobileSheet = mounted && isMobile && open
    ? createPortal(
        <AnimatePresence>
          {open && (
            <motion.div
              key="mobile-backdrop"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.18 }}
              className="fixed inset-0 z-[120] bg-pe-black/45 backdrop-blur-sm"
              onClick={closePopover}
              aria-hidden="true"
            >
              <motion.div
                key="mobile-sheet"
                initial={{ y: '100%' }}
                animate={{ y: 0 }}
                exit={{ y: '100%' }}
                transition={{ duration: 0.28, ease: EASING }}
                id="cart-popover-content"
                role="dialog"
                aria-modal="true"
                aria-label={cartLabel}
                className="absolute bottom-0 left-0 right-0 bg-pe-beige flex flex-col"
                style={{ height: '70vh' }}
                onClick={(e) => e.stopPropagation()}
              >
                {/* Grip bar */}
                <div className="flex justify-center pt-3 pb-1 flex-shrink-0">
                  <div className="w-10 h-1 bg-pe-cream" aria-hidden="true" />
                </div>

                {/* Sheet header */}
                <div className="flex items-center justify-between px-4 py-3 border-b border-pe-charcoal/10 flex-shrink-0">
                  <h2 className="font-display text-pe-black text-base font-semibold tracking-wide">
                    {locale === 'es' ? 'Tu carrito' : 'Your cart'}
                  </h2>
                  <button
                    onClick={closePopover}
                    aria-label={locale === 'es' ? 'Cerrar carrito' : 'Close cart'}
                    className="text-pe-charcoal/40 hover:text-pe-charcoal transition-colors p-1"
                  >
                    <X size={18} />
                  </button>
                </div>

                {/* Scrollable items */}
                <div className="flex-1 overflow-y-auto">
                  {items.length === 0 ? (
                    <CartEmptyState locale={locale} onClose={closePopover} />
                  ) : (
                    items.map((item) => (
                      <CartItemRow
                        key={item.id}
                        item={item}
                        locale={locale}
                        onRemove={() => removeItem(item.id)}
                        onNavigate={closePopover}
                        cartHref={viewCartHref}
                      />
                    ))
                  )}
                </div>

                {/* Sticky footer */}
                {items.length > 0 && (
                  <div className="border-t border-pe-charcoal/10 px-4 py-4 flex-shrink-0 safe-area-pb">
                    <div className="flex justify-between items-center mb-3">
                      <span className="font-sans text-xs tracking-widest uppercase text-pe-charcoal/70">
                        Subtotal
                      </span>
                      <span className="font-sans text-sm font-medium text-pe-black">
                        {priceFormat(subtotal, 'CLP', locale)}
                      </span>
                    </div>
                    <a
                      href={viewCartHref}
                      onClick={closePopover}
                      className="block w-full text-center font-sans text-[0.65rem] tracking-[0.22em] uppercase px-4 py-3 bg-pe-rose text-pe-white hover:bg-pe-rose-deep transition-colors duration-200"
                    >
                      {locale === 'es' ? 'Ver carrito' : 'View cart'}
                    </a>
                  </div>
                )}
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>,
        document.body
      )
    : null;

  return (
    <div
      ref={containerRef}
      className="relative"
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      {trigger}

      {/* Desktop popover */}
      {!isMobile && (
        <AnimatePresence>
          {open && (
            <motion.div
              id="cart-popover-content"
              role="dialog"
              aria-modal="true"
              aria-label={cartLabel}
              initial={{ opacity: 0, y: -8, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -4, scale: 0.97 }}
              transition={{ duration: 0.22, ease: EASING }}
              className="absolute right-0 top-full mt-2 z-[100] bg-pe-beige shadow-editorial flex flex-col"
              style={{ width: 340, maxHeight: 'calc(100vh - 8rem)' }}
            >
              {/* Header */}
              <div className="flex items-center justify-between px-4 py-3 border-b border-pe-charcoal/10 flex-shrink-0">
                <h2 className="font-display text-pe-black text-base font-semibold tracking-wide">
                  {locale === 'es' ? 'Tu carrito' : 'Your cart'}
                </h2>
                <button
                  onClick={closePopover}
                  aria-label={locale === 'es' ? 'Cerrar carrito' : 'Close cart'}
                  className="text-pe-charcoal/40 hover:text-pe-charcoal transition-colors"
                >
                  <X size={15} />
                </button>
              </div>

              {/* Items */}
              <div className="flex-1 overflow-y-auto" style={{ maxHeight: 320 }}>
                {items.length === 0 ? (
                  <CartEmptyState locale={locale} onClose={closePopover} />
                ) : (
                  items.map((item) => (
                    <CartItemRow
                      key={item.id}
                      item={item}
                      locale={locale}
                      onRemove={() => removeItem(item.id)}
                      onNavigate={closePopover}
                      cartHref={viewCartHref}
                    />
                  ))
                )}
              </div>

              {/* Footer */}
              {items.length > 0 && (
                <div className="border-t border-pe-charcoal/10 px-4 py-3 flex-shrink-0">
                  <div className="flex justify-between items-center mb-3">
                    <span className="font-sans text-xs tracking-widest uppercase text-pe-charcoal/70">
                      Subtotal
                    </span>
                    <span className="font-sans text-sm font-medium text-pe-black">
                      {priceFormat(subtotal, 'CLP', locale)}
                    </span>
                  </div>
                  <a
                    href={viewCartHref}
                    onClick={closePopover}
                    className="block w-full text-center font-sans text-[0.65rem] tracking-[0.22em] uppercase px-4 py-2.5 bg-pe-rose text-pe-white hover:bg-pe-rose-deep transition-colors duration-200"
                  >
                    {locale === 'es' ? 'Ver carrito' : 'View cart'}
                  </a>
                </div>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      )}

      {/* Mobile sheet portal */}
      {mobileSheet}
    </div>
  );
}
