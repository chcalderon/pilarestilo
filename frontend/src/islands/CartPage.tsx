import { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowRight, Loader2, PackageX, Trash2, Undo2 } from 'lucide-react';
import { useCartStore, verifyStockForItem, type CartItem } from '../lib/cartStore';
import { useStockCheck } from '../lib/useStockCheck';
import StockUnavailableModal from './cart/StockUnavailableModal';
import { useAuthStore, readAuthTokenCookie } from '../lib/authStore';
import { formatPrice } from '../lib/formatPrice';
import StockBadge, { stockImageClass } from './cart/StockBadge';
import type { Locale } from '../i18n/index';

interface Props {
  readonly locale: Locale;
}

const labels = {
  es: {
    title: 'Carrito',
    empty: 'Tu carrito está vacío',
    emptyLink: 'Explorar productos',
    subtotal: 'Subtotal',
    continueToCheckout: 'Continuar compra',
    continueHint: 'Elegirás envío y pago en los siguientes pasos.',
    quantity: 'Cantidad',
    remove: 'Quitar',
    availableStockPrefix: 'Disponible',
    removeUnavailable: 'Quitar del carrito',
    findReplacement: 'Ver alternativas',
    stockAdjusted: 'Este producto ya no está disponible.',
    needsVariantHint: 'Este producto se vende por talla y esta línea no tiene una. Quítala y vuelve a agregarlo eligiendo la talla.',
    chooseSize: 'Elegir talla',
    resolveStockFirst: 'Resuelve los productos sin stock para continuar.',
    unavailableBanner: 'Un producto de tu carrito ya no está disponible.',
    unavailableBannerPlural: 'productos de tu carrito ya no están disponibles.',
    checkingStock: 'Revisando disponibilidad…',
    removed: 'Producto quitado',
    undo: 'Deshacer',
  },
  en: {
    title: 'Cart',
    empty: 'Your cart is empty',
    emptyLink: 'Browse products',
    subtotal: 'Subtotal',
    continueToCheckout: 'Continue to checkout',
    continueHint: 'You will choose shipping and payment in the next steps.',
    quantity: 'Quantity',
    remove: 'Remove',
    availableStockPrefix: 'Available',
    removeUnavailable: 'Remove from cart',
    findReplacement: 'See alternatives',
    stockAdjusted: 'This product is no longer available.',
    needsVariantHint: 'This product is sold by size and this line has none. Remove it and add it again choosing a size.',
    chooseSize: 'Choose a size',
    resolveStockFirst: 'Resolve the out-of-stock items to continue.',
    unavailableBanner: 'One item in your cart is no longer available.',
    unavailableBannerPlural: 'items in your cart are no longer available.',
    checkingStock: 'Checking availability…',
    removed: 'Item removed',
    undo: 'Undo',
  },
} as const;

export default function CartPage({ locale }: Props) {
  const l = labels[locale === 'es' ? 'es' : 'en'];

  const items = useCartStore((s) => s.items);
  const updateQuantity = useCartStore((s) => s.updateQuantity);
  const removeItem = useCartStore((s) => s.removeItem);
  const addItem = useCartStore((s) => s.addItem);

  const storeToken = useAuthStore((s) => s.token);
  const [cookieToken, setCookieToken] = useState<string | null>(null);
  useEffect(() => setCookieToken(readAuthTokenCookie()), []);
  const hasSession = Boolean(storeToken ?? cookieToken);

  const { issues, checking, clearIssue } = useStockCheck(items);
  /** The last line removed, kept only long enough to offer an undo. */
  const [undoable, setUndoable] = useState<CartItem | null>(null);
  const undoTimerRef = useRef<number | null>(null);
  const [stockModal, setStockModal] = useState<{
    productName: string;
    availableQty: number;
    requestedQty: number;
  } | null>(null);

  /** Guards against an out-of-order response overwriting a newer check for the same item. */
  const qtyVerifyCounterRef = useRef<Record<string, number>>({});

  const subtotal = useMemo(
    () => items.reduce((sum, i) => sum + i.price.amount * i.quantity, 0),
    [items]
  );
  const currency = items[0]?.price.currency ?? 'CLP';
  const conflictCount = Object.keys(issues).length;

  /**
   * Removal is one tap, undone by a toast rather than guarded by a confirmation dialog. The
   * customer is being asked to drop something the store cannot sell them; making them confirm
   * that is friction over a decision that was never really theirs.
   */
  function removeWithUndo(item: CartItem) {
    clearIssue(item.id);
    removeItem(item.id);
    setUndoable(item);
    if (undoTimerRef.current) window.clearTimeout(undoTimerRef.current);
    undoTimerRef.current = window.setTimeout(() => setUndoable(null), 6000);
  }

  function undoRemove() {
    if (!undoable) return;
    const { quantity, ...rest } = undoable;
    addItem(rest);
    if (quantity > 1) updateQuantity(undoable.id, quantity);
    setUndoable(null);
  }

  useEffect(() => () => {
    if (undoTimerRef.current) window.clearTimeout(undoTimerRef.current);
  }, []);

  async function increaseQuantity(item: CartItem) {
    clearIssue(item.id);
    const productId = item.productId || item.id.split('::')[0];

    if (productId) {
      const requestId = (qtyVerifyCounterRef.current[item.id] ?? 0) + 1;
      qtyVerifyCounterRef.current[item.id] = requestId;

      const variant =
        item.variantColor || item.variantSize
          ? { color: item.variantColor, size: item.variantSize }
          : null;
      const result = await verifyStockForItem(productId, variant, item.quantity + 1);

      if (qtyVerifyCounterRef.current[item.id] !== requestId) return;
      if (!result.ok) {
        setStockModal({
          productName: result.productName,
          availableQty: result.reason === 'INSUFFICIENT' ? result.availableQty : 0,
          requestedQty: item.quantity + 1,
        });
        return;
      }
    }
    updateQuantity(item.id, item.quantity + 1);
  }

  /**
   * Unauthenticated customers are sent through login first and land back here. The gate also
   * exists server-side in middleware.ts; this only spares them a redirect round trip.
   */
  const checkoutPath = `/${locale}/checkout`;
  const checkoutHref = hasSession
    ? checkoutPath
    : `/${locale}/auth/login?redirect=${encodeURIComponent(checkoutPath)}`;

  if (items.length === 0) {
    return (
      <div className="py-12 bg-pe-beige min-h-screen">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
          <h1 className="font-display text-pe-black text-3xl md:text-4xl font-semibold mb-8">
            {l.title}
          </h1>
          <div className="bg-pe-white p-12 text-center">
            <p className="font-sans text-pe-charcoal mb-6">{l.empty}</p>
            <a
              href={`/${locale}/products`}
              className="inline-flex items-center min-h-11 px-6 bg-pe-black text-pe-white
                font-sans text-[0.7rem] tracking-[0.16em] uppercase"
            >
              {l.emptyLink}
            </a>
          </div>
        </div>
      </div>
    );
  }

  return (
    <>
      <div className="py-12 bg-pe-beige min-h-screen">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
          <h1 className="font-display text-pe-black text-3xl md:text-4xl font-semibold mb-8">
            {l.title}
          </h1>

          {checking && (
            <p
              role="status"
              aria-live="polite"
              className="flex items-center gap-2 font-sans text-[0.75rem] text-pe-muted mb-4"
            >
              <Loader2 size={13} className="animate-spin" aria-hidden="true" />
              {l.checkingStock}
            </p>
          )}

          {conflictCount > 0 && (
            <div
              role="alert"
              className="flex items-start gap-3 border-l-4 border-[#8f2d3b] bg-[#ffe9ec] p-4 mb-6"
            >
              <PackageX size={18} className="text-[#8f2d3b] shrink-0 mt-0.5" aria-hidden="true" />
              <p className="font-sans text-sm text-[#732731]">
                {conflictCount === 1
                  ? l.unavailableBanner
                  : `${conflictCount} ${l.unavailableBannerPlural}`}
              </p>
            </div>
          )}

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            <div className="lg:col-span-2 flex flex-col gap-4">
              {items.map((item) => {
                const conflict = issues[item.id];

                return (
                  <div
                    key={item.id}
                    className={`flex gap-4 p-4 border-l-4 border-y border-r ${
                      conflict
                        ? 'bg-[#fff6f7] border-[#cb6070]/45 border-l-[#8f2d3b]'
                        : 'bg-pe-white border-transparent'
                    }`}
                  >
                    <img
                      src={item.imageUrl}
                      alt={item.name}
                      className={`w-20 h-24 object-cover flex-shrink-0 transition-[filter,opacity]
                        duration-200 ${stockImageClass(conflict)}`}
                      width="80"
                      height="96"
                      loading="lazy"
                    />
                    <div className="flex-1 min-w-0 flex flex-col gap-1">
                      <span className="font-sans text-[10px] tracking-[0.3em] uppercase text-pe-gold-ink">
                        {item.brand}
                      </span>
                      {conflict && (
                        <span className="mb-0.5">
                          <StockBadge issue={conflict} locale={locale} />
                        </span>
                      )}
                      <p
                        className={`font-display text-sm font-semibold truncate ${
                          conflict?.type === 'SOLD_OUT'
                            ? 'text-pe-muted line-through'
                            : 'text-pe-black'
                        }`}
                      >
                        {item.name}
                      </p>
                      {item.variantLabel && (
                        <p className="font-sans text-[11px] tracking-wide text-pe-muted">
                          {item.variantLabel}
                        </p>
                      )}
                      <p className="font-sans text-pe-black text-sm">
                        {formatPrice(item.price.amount, item.price.currency, locale)}
                      </p>

                      {conflict && (
                        <div
                          role="status"
                          aria-live="polite"
                          className="mt-2 border border-[#cb6070]/45 bg-[#ffe9ec] px-2.5 py-2"
                        >
                          {/*
                            * One message per reason. NEEDS_VARIANT used to fall through to the
                            * "Disponible: 0" branch, so a line whose only problem was a missing
                            * size announced that the product had no stock — contradicting the
                            * badge right above it and sending the customer to look for a
                            * replacement that was never needed.
                            */}
                          <p className="font-sans text-xs text-[#732731]">
                            {(() => {
                              if (conflict.type === 'SOLD_OUT') return l.stockAdjusted;
                              if (conflict.type === 'NEEDS_VARIANT') return l.needsVariantHint;
                              return `${l.availableStockPrefix}: ${conflict.availableQty}`;
                            })()}
                          </p>
                          <div className="mt-2 flex items-center gap-2">
                            <button
                              type="button"
                              onClick={() => removeWithUndo(item)}
                              className="inline-flex items-center gap-1.5 font-sans text-[10px] tracking-[0.14em]
                                uppercase min-h-11 px-2.5 border border-[#8f2d3b]/40 text-[#8f2d3b]
                                hover:bg-[#8f2d3b] hover:text-white transition-colors
                                focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose"
                            >
                              <Trash2 size={13} aria-hidden="true" />
                              {l.removeUnavailable}
                            </button>
                            <a
                              href={
                                conflict.type === 'NEEDS_VARIANT'
                                  ? `/${locale}/products/${item.productId ?? item.id.split('::')[0]}`
                                  : `/${locale}/products`
                              }
                              className="font-sans text-[10px] tracking-[0.14em] uppercase px-2 py-1 border border-pe-black/20 text-pe-muted hover:border-pe-gold hover:text-pe-gold-ink transition-colors"
                            >
                              {conflict.type === 'NEEDS_VARIANT' ? l.chooseSize : l.findReplacement}
                            </a>
                          </div>
                        </div>
                      )}

                      <div className="mt-auto flex items-center justify-between gap-4">
                        <div className="flex items-center gap-2" aria-label={l.quantity}>
                          <button
                            type="button"
                            onClick={() => {
                              clearIssue(item.id);
                              updateQuantity(item.id, item.quantity - 1);
                            }}
                            className="w-7 h-7 border border-pe-black/20 flex items-center justify-center font-sans text-sm hover:border-pe-gold hover:text-pe-gold-ink transition-colors"
                            aria-label={locale === 'es' ? 'Disminuir cantidad' : 'Decrease quantity'}
                          >
                            -
                          </button>
                          <span className="font-sans text-sm w-5 text-center">{item.quantity}</span>
                          <button
                            type="button"
                            onClick={() => void increaseQuantity(item)}
                            className="w-7 h-7 border border-pe-black/20 flex items-center justify-center font-sans text-sm hover:border-pe-gold hover:text-pe-gold-ink transition-colors"
                            aria-label={locale === 'es' ? 'Aumentar cantidad' : 'Increase quantity'}
                          >
                            +
                          </button>
                        </div>

                        <span className="font-sans text-sm font-semibold tabular-nums">
                          {formatPrice(item.price.amount * item.quantity, item.price.currency, locale)}
                        </span>

                        <button
                          type="button"
                          onClick={() => {
                            clearIssue(item.id);
                            removeItem(item.id);
                          }}
                          className="font-sans text-[10px] tracking-widest uppercase text-pe-muted hover:text-pe-gold-ink transition-colors"
                          aria-label={`${l.remove} ${item.name}`}
                        >
                          {l.remove}
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="lg:col-span-1">
              <div
                className="bg-pe-white p-6 lg:sticky"
                style={{ top: 'calc(var(--pe-site-header-height, 0px) + 1rem)' }}
              >
                {/*
                 * Subtotal only. Discounts, shipping and payment belong to the checkout steps,
                 * where the customer has actually chosen them — showing a total here meant
                 * showing a figure that later changed.
                 */}
                <div className="flex justify-between items-baseline mb-6">
                  <span className="font-display text-pe-black text-base font-semibold">
                    {l.subtotal}
                  </span>
                  <span className="font-display text-pe-black text-xl font-semibold tabular-nums">
                    {formatPrice(subtotal, currency, locale)}
                  </span>
                </div>

                {conflictCount > 0 && (
                  <p
                    role="status"
                    className="font-sans text-[0.72rem] text-[#8f2d3b] mb-3"
                  >
                    {l.resolveStockFirst}
                  </p>
                )}

                {conflictCount > 0 ? (
                  <span
                    aria-disabled="true"
                    className="w-full inline-flex items-center justify-center min-h-12 px-6
                      bg-pe-black/40 text-pe-white font-sans text-[0.7rem] tracking-[0.16em]
                      uppercase cursor-not-allowed"
                  >
                    {l.continueToCheckout}
                  </span>
                ) : (
                  <a
                    href={checkoutHref}
                    className="w-full inline-flex items-center justify-center gap-2 min-h-12 px-6
                      bg-pe-black text-pe-white font-sans text-[0.7rem] tracking-[0.16em] uppercase
                      hover:bg-pe-black/90 transition-colors
                      focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose focus-visible:ring-offset-2"
                  >
                    {l.continueToCheckout}
                    <ArrowRight size={14} />
                  </a>
                )}

                <p className="font-sans text-[0.72rem] text-pe-muted mt-3 text-center">
                  {l.continueHint}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {undoable && (
        <div
          role="status"
          aria-live="polite"
          className="fixed left-1/2 -translate-x-1/2 bottom-6 z-50 flex items-center gap-3
            bg-pe-black text-pe-white px-4 py-3 shadow-xl max-w-[calc(100vw-2rem)]"
        >
          <span className="font-sans text-[0.78rem] truncate">
            {l.removed}: {undoable.name}
          </span>
          <button
            type="button"
            onClick={undoRemove}
            className="inline-flex items-center gap-1.5 shrink-0 min-h-11 px-2 font-sans
              text-[0.68rem] tracking-[0.16em] uppercase text-pe-white underline underline-offset-4
              focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose"
          >
            <Undo2 size={13} aria-hidden="true" />
            {l.undo}
          </button>
        </div>
      )}

      {stockModal && (
        <StockUnavailableModal
          open
          productName={stockModal.productName}
          availableQty={stockModal.availableQty}
          requestedQty={stockModal.requestedQty}
          onClose={() => setStockModal(null)}
        />
      )}
    </>
  );
}
