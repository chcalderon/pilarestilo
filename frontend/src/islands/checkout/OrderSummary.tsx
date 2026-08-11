import { useState } from 'react';
import { Loader2, X } from 'lucide-react';
import type { DiscountCodeDto } from '../../lib/api';
import type { CheckoutTotals } from '../../lib/checkoutTotals';
import { formatPrice } from '../../lib/formatPrice';
import type { Locale } from '../../i18n/index';

interface Props {
  locale: Locale;
  currency: string;
  totals: CheckoutTotals;
  appliedDiscount: DiscountCodeDto | null;
  applying: boolean;
  error: string;
  onApply: (code: string) => void;
  onRemove: () => void;
}

const copy = {
  es: {
    title: 'Resumen',
    subtotal: 'Subtotal',
    employeeDiscount: 'Descuento trabajador (10%)',
    discountLabel: 'Código de descuento',
    total: 'Total',
    placeholder: 'Ingresa tu código',
    apply: 'Aplicar',
    remove: 'Quitar código',
    /** Reserve-then-settle: the code is only consumed once the payment lands. */
    pendingNote: 'El código se confirma al completar el pago.',
  },
  en: {
    title: 'Summary',
    subtotal: 'Subtotal',
    employeeDiscount: 'Employee discount (10%)',
    discountLabel: 'Discount code',
    total: 'Total',
    placeholder: 'Enter your code',
    apply: 'Apply',
    remove: 'Remove code',
    pendingNote: 'The code is confirmed once payment completes.',
  },
} as const;

export default function OrderSummary({
  locale,
  currency,
  totals,
  appliedDiscount,
  applying,
  error,
  onApply,
  onRemove,
}: Props) {
  const l = copy[locale === 'es' ? 'es' : 'en'];
  const [draft, setDraft] = useState('');

  function submit(event: React.FormEvent) {
    event.preventDefault();
    const code = draft.trim();
    if (code && !applying) onApply(code);
  }

  return (
    <div
      className="bg-pe-white p-6 lg:sticky"
      style={{ top: 'calc(var(--pe-site-header-height, 0px) + 1rem)' }}
    >
      <h2 className="font-display text-pe-black text-xl font-semibold mb-6">{l.title}</h2>

      <div className="flex justify-between font-sans text-sm mb-4">
        <span className="text-pe-charcoal/75">{l.subtotal}</span>
        <span className="font-semibold tabular-nums">
          {formatPrice(totals.subtotal, currency, locale)}
        </span>
      </div>

      {totals.employeeDiscount > 0 && (
        <div className="flex justify-between font-sans text-sm mb-4">
          <span className="text-pe-charcoal/75">{l.employeeDiscount}</span>
          <span className="font-semibold text-green-700 tabular-nums">
            −{formatPrice(totals.employeeDiscount, currency, locale)}
          </span>
        </div>
      )}

      <div className="mb-4">
        <p className="font-sans text-[0.68rem] tracking-[0.16em] uppercase text-pe-charcoal/75 mb-2">
          {l.discountLabel}
        </p>

        {appliedDiscount ? (
          <>
            <div className="flex items-center justify-between gap-2">
              <span className="font-mono text-[0.78rem] text-green-700 border border-green-200 bg-green-50 px-2 py-1.5 flex-1 truncate">
                {appliedDiscount.code}
              </span>
              <span className="font-sans text-sm font-semibold text-green-700 tabular-nums">
                −{formatPrice(totals.codeDiscount, currency, locale)}
              </span>
              <button
                type="button"
                onClick={onRemove}
                aria-label={l.remove}
                className="shrink-0 w-11 h-11 -mr-2 flex items-center justify-center text-pe-charcoal/60
                  hover:text-pe-black transition-colors
                  focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-pe-rose"
              >
                <X size={16} />
              </button>
            </div>
            <p className="font-sans text-[0.7rem] text-pe-charcoal/60 mt-2">{l.pendingNote}</p>
          </>
        ) : (
          <form onSubmit={submit} className="flex gap-2">
            <label htmlFor="checkout-discount-code" className="sr-only">
              {l.discountLabel}
            </label>
            <input
              id="checkout-discount-code"
              type="text"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder={l.placeholder}
              autoComplete="off"
              autoCapitalize="characters"
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? 'checkout-discount-error' : undefined}
              className="flex-1 min-w-0 h-11 border border-pe-charcoal/25 bg-pe-white px-3
                font-mono text-[0.78rem] uppercase text-pe-black placeholder:font-sans
                placeholder:normal-case placeholder:text-pe-charcoal/40
                focus:outline-none focus:border-pe-black"
            />
            <button
              type="submit"
              disabled={applying || !draft.trim()}
              className="shrink-0 h-11 px-4 bg-pe-black text-pe-white font-sans text-[0.7rem]
                tracking-[0.16em] uppercase transition-opacity
                disabled:opacity-40 disabled:cursor-not-allowed
                focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-pe-rose focus-visible:ring-offset-2"
            >
              {applying ? <Loader2 size={14} className="animate-spin" /> : l.apply}
            </button>
          </form>
        )}

        {error && (
          <p
            id="checkout-discount-error"
            role="alert"
            className="font-sans text-[0.72rem] text-[#8f2d3b] mt-2"
          >
            {error}
          </p>
        )}
      </div>

      <div className="border-t border-pe-charcoal/15 pt-4 flex justify-between items-baseline">
        <span className="font-display text-pe-black text-base font-semibold">{l.total}</span>
        <span className="font-display text-pe-black text-xl font-semibold tabular-nums">
          {formatPrice(totals.total, currency, locale)}
        </span>
      </div>
    </div>
  );
}
