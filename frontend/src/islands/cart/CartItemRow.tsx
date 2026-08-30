import { Trash2 } from 'lucide-react';
import type { CartItem } from '../../lib/cartStore';
import type { Locale } from '../../i18n/index';

interface Props {
  readonly item: CartItem;
  readonly locale: Locale;
  readonly onRemove: () => void;
  readonly onNavigate: () => void;
  readonly cartHref: string;
}

export default function CartItemRow({ item, locale, onRemove, onNavigate, cartHref }: Props) {
  const priceFormat = (amount: number, currency = 'CLP') =>
    new Intl.NumberFormat(locale === 'es' ? 'es-CL' : 'en-US', {
      style: 'currency',
      currency,
      maximumFractionDigits: 0,
    }).format(amount);

  return (
    <div className="flex gap-3 px-4 py-3 border-b border-pe-charcoal/8 last:border-b-0 group">
      {/* Image */}
      <a
        href={cartHref}
        onClick={onNavigate}
        className="flex-shrink-0 block"
        tabIndex={-1}
        aria-hidden="true"
      >
        {item.imageUrl ? (
          <img
            src={item.imageUrl}
            alt={item.name}
            width={60}
            height={76}
            className="w-[60px] h-[76px] object-cover"
            loading="lazy"
          />
        ) : (
          <div className="w-[60px] h-[76px] bg-pe-cream" />
        )}
      </a>

      {/* Info */}
      <div className="flex-1 min-w-0 flex flex-col justify-between py-0.5">
        <div>
          {item.brand && (
            <p className="font-sans text-[0.6rem] tracking-[0.22em] uppercase text-pe-gold-ink mb-0.5 truncate">
              {item.brand}
            </p>
          )}
          <a
            href={cartHref}
            onClick={onNavigate}
            className="block font-sans text-[0.76rem] text-pe-black leading-snug truncate hover:text-pe-rose-ink transition-colors"
          >
            {item.name}
          </a>
          {item.variantLabel && (
            <p className="font-sans text-[0.63rem] text-pe-muted mt-0.5 truncate">
              {item.variantLabel}
            </p>
          )}
        </div>

        <div className="flex items-end justify-between mt-1.5">
          <div>
            <p className="font-sans text-[0.68rem] text-pe-muted">
              {locale === 'es' ? 'Cant' : 'Qty'}: {item.quantity}
            </p>
            <p className="font-sans text-[0.75rem] font-medium text-pe-black">
              {priceFormat(item.price.amount * item.quantity, item.price.currency)}
            </p>
          </div>
          <button
            type="button"
            onClick={onRemove}
            aria-label={`${locale === 'es' ? 'Eliminar' : 'Remove'} ${item.name}`}
            className="flex items-center justify-center min-h-11 min-w-11 -mr-2 -mb-1 text-pe-muted hover:text-pe-rose-ink transition-colors duration-200 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose"
          >
            <Trash2 size={13} strokeWidth={1.5} />
          </button>
        </div>
      </div>
    </div>
  );
}
