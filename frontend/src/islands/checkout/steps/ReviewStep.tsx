import { AlertTriangle, ArrowLeft, Loader2, Lock, Trash2 } from 'lucide-react';
import type { CustomerAddressDto } from '../../../lib/api';
import type { CartItem } from '../../../lib/cartStore';
import type { CheckoutPaymentMethod } from '../../../lib/checkoutStore';
import { formatPrice } from '../../../lib/formatPrice';
import type { StockIssues } from '../../../lib/useStockCheck';
import StockBadge, { stockImageClass } from '../../cart/StockBadge';
import type { Locale } from '../../../i18n/index';

interface Props {
  locale: Locale;
  items: CartItem[];
  address: CustomerAddressDto | null;
  method: CheckoutPaymentMethod;
  courierName: string;
  zoneName: string;
  total: number;
  currency: string;
  submitting: boolean;
  error: string;
  /** Lines the last check found unavailable, keyed by cart line id. */
  stockIssues: StockIssues;
  onRemoveItem: (lineId: string) => void;
  onBack: () => void;
  /** Returns to the shipping step, for when the summary has no address to show. */
  onFixShipping: () => void;
  onSubmit: () => void;
}

const copy = {
  es: {
    heading: 'Revisa tu pedido',
    items: 'Productos',
    shipTo: 'Enviar a',
    addressMissing: 'Falta la dirección de envío. Sin ella no podemos despachar tu pedido.',
    addressMissingAction: 'Elegir dirección',
    shipping: 'Envío',
    payment: 'Pago',
    transfer: 'Transferencia bancaria',
    gateway: 'Pago en línea',
    shippingOnDelivery: 'El envío se paga al recibir.',
    back: 'Volver a pago',
    pay: 'Confirmar pedido',
    submitting: 'Procesando…',
    qty: 'Cantidad',
    removeLine: 'Quitar del pedido',
  },
  en: {
    heading: 'Review your order',
    items: 'Items',
    shipTo: 'Ship to',
    addressMissing: 'The shipping address is missing. We cannot dispatch without it.',
    addressMissingAction: 'Choose an address',
    shipping: 'Shipping',
    payment: 'Payment',
    transfer: 'Bank transfer',
    gateway: 'Online payment',
    shippingOnDelivery: 'Shipping is paid on delivery.',
    back: 'Back to payment',
    pay: 'Place order',
    submitting: 'Processing…',
    qty: 'Quantity',
    removeLine: 'Remove from order',
  },
} as const;

export default function ReviewStep({
  locale,
  items,
  address,
  method,
  courierName,
  zoneName,
  total,
  currency,
  submitting,
  error,
  stockIssues,
  onRemoveItem,
  onBack,
  onFixShipping,
  onSubmit,
}: Props) {
  const l = copy[locale === 'es' ? 'es' : 'en'];

  return (
    <div className="bg-pe-white p-6">
      <h2 className="font-display text-pe-black text-xl font-semibold mb-6">{l.heading}</h2>

      <section className="mb-6">
        <h3 className="font-sans text-[0.68rem] tracking-[0.16em] uppercase text-pe-charcoal/75 mb-3">
          {l.items}
        </h3>
        <ul className="space-y-3">
          {items.map((item) => {
            const issue = stockIssues[item.id];
            return (
            <li
              key={item.id}
              className={`flex gap-3 items-start ${
                issue ? 'border-l-4 border-[#8f2d3b] bg-[#fff6f7] -ml-3 pl-3 py-2' : ''
              }`}
            >
              {item.imageUrl && (
                <img
                  src={item.imageUrl}
                  alt=""
                  width={48}
                  height={64}
                  loading="lazy"
                  className={`w-12 h-16 object-cover bg-pe-cream shrink-0 ${stockImageClass(issue)}`}
                />
              )}
              <div className="flex-1 min-w-0">
                {issue && (
                  <span className="block mb-1">
                    <StockBadge issue={issue} locale={locale} />
                  </span>
                )}
                <p
                  className={`font-sans text-sm truncate ${
                    issue?.type === 'SOLD_OUT'
                      ? 'text-pe-charcoal/50 line-through'
                      : 'text-pe-black'
                  }`}
                >
                  {item.name}
                </p>
                {item.variantLabel && (
                  <p className="font-sans text-[0.72rem] text-pe-charcoal/60">{item.variantLabel}</p>
                )}
                <p className="font-sans text-[0.72rem] text-pe-charcoal/60">
                  {l.qty}: {item.quantity}
                </p>

                {issue && (
                  <div className="mt-1" role="status" aria-live="polite">
                    <button
                      type="button"
                      onClick={() => onRemoveItem(item.id)}
                      className="mt-1 inline-flex items-center gap-1.5 min-h-11 pr-2 font-sans
                        text-[0.66rem] tracking-[0.14em] uppercase text-[#8f2d3b] underline
                        underline-offset-4 focus-visible:outline-none focus-visible:ring-2
                        focus-visible:ring-pe-rose"
                    >
                      <Trash2 size={13} aria-hidden="true" />
                      {l.removeLine}
                    </button>
                  </div>
                )}
              </div>
              <p className="font-sans text-sm text-pe-black tabular-nums shrink-0">
                {formatPrice(item.price.amount * item.quantity, item.price.currency, locale)}
              </p>
            </li>
            );
          })}
        </ul>
      </section>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 mb-6 border-t border-pe-charcoal/15 pt-6">
        <section>
          <h3 className="font-sans text-[0.68rem] tracking-[0.16em] uppercase text-pe-charcoal/75 mb-2">
            {l.shipTo}
          </h3>
          {address ? (
            <address className="not-italic font-sans text-[0.8rem] text-pe-charcoal leading-relaxed">
              <span className="block font-semibold text-pe-black">{address.recipientName}</span>
              {address.line1}
              {address.line2 ? `, ${address.line2}` : ''}
              <br />
              {address.comuna}, {address.city}
              <br />
              {address.phone}
            </address>
          ) : (
            /*
             * A dash said nothing and left Pagar enabled, so the first sign anything was wrong
             * arrived after the click, as "vuelve al primer paso". The address can go missing
             * legitimately — deleted from the address book after it was chosen — so this states
             * the problem and offers the way out rather than only refusing.
             */
            <div role="alert" className="border border-[#8f2d3b]/40 bg-[#8f2d3b]/5 p-3">
              <p className="font-sans text-[0.8rem] text-[#8f2d3b] mb-2">{l.addressMissing}</p>
              <button
                type="button"
                onClick={onFixShipping}
                className="font-sans text-[0.7rem] tracking-[0.14em] uppercase underline
                  text-pe-charcoal hover:text-pe-black
                  focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-pe-rose"
              >
                {l.addressMissingAction}
              </button>
            </div>
          )}
        </section>

        <section>
          <h3 className="font-sans text-[0.68rem] tracking-[0.16em] uppercase text-pe-charcoal/75 mb-2">
            {l.shipping}
          </h3>
          <p className="font-sans text-[0.8rem] text-pe-charcoal">
            {courierName}
            {zoneName ? ` · ${zoneName}` : ''}
          </p>
          <p className="font-sans text-[0.74rem] text-pe-charcoal/60 mt-1">{l.shippingOnDelivery}</p>

          <h3 className="font-sans text-[0.68rem] tracking-[0.16em] uppercase text-pe-charcoal/75 mt-4 mb-2">
            {l.payment}
          </h3>
          <p className="font-sans text-[0.8rem] text-pe-charcoal">
            {method === 'TRANSFER' ? l.transfer : l.gateway}
          </p>
        </section>
      </div>

      {error && (
        <div
          role="alert"
          className="flex items-start gap-2 border-l-2 border-[#cb6070] bg-[#fff0f2] p-3 mb-4"
        >
          <AlertTriangle size={15} className="text-[#8f2d3b] shrink-0 mt-0.5" aria-hidden="true" />
          <p className="font-sans text-[0.78rem] text-[#732731]">{error}</p>
        </div>
      )}

      <div className="flex flex-col-reverse sm:flex-row gap-3 items-stretch sm:items-center">
        <button
          type="button"
          onClick={onBack}
          disabled={submitting}
          className="inline-flex items-center justify-center gap-2 min-h-12 px-5
            border border-pe-charcoal/25 font-sans text-[0.7rem] tracking-[0.16em] uppercase
            text-pe-charcoal hover:border-pe-black transition-colors disabled:opacity-40
            focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-pe-rose focus-visible:ring-offset-2"
        >
          <ArrowLeft size={14} />
          {l.back}
        </button>
        <button
          type="button"
          onClick={onSubmit}
          disabled={submitting || !address || Object.keys(stockIssues).length > 0}
          className="flex-1 inline-flex items-center justify-center gap-2 min-h-12 px-8
            bg-pe-black text-pe-white font-sans text-[0.7rem] tracking-[0.16em] uppercase
            transition-opacity disabled:opacity-50 disabled:cursor-wait
            focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-pe-rose focus-visible:ring-offset-2"
        >
          {submitting ? (
            <>
              <Loader2 size={14} className="animate-spin" />
              {l.submitting}
            </>
          ) : (
            <>
              <Lock size={14} />
              {l.pay} · {formatPrice(total, currency, locale)}
            </>
          )}
        </button>
      </div>
    </div>
  );
}
