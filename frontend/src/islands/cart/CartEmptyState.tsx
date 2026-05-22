import { ShoppingBag } from 'lucide-react';
import type { Locale } from '../../i18n/index';

interface Props {
  locale: Locale;
  onClose: () => void;
}

export default function CartEmptyState({ locale, onClose }: Props) {
  return (
    <div className="flex flex-col items-center justify-center py-10 px-4 text-center">
      <ShoppingBag size={36} strokeWidth={1} className="text-pe-charcoal/25 mb-3" aria-hidden="true" />
      <p className="font-display text-pe-black text-base mb-1" aria-live="polite">
        {locale === 'es' ? 'Tu carrito está vacío' : 'Your cart is empty'}
      </p>
      <p className="font-sans text-[0.68rem] tracking-wide text-pe-charcoal/55 mb-5">
        {locale === 'es' ? 'Descubre nuestra curaduría' : 'Discover our curation'}
      </p>
      <a
        href={`/${locale}/products`}
        onClick={onClose}
        className="font-sans text-[0.62rem] tracking-[0.22em] uppercase px-4 py-2 border border-pe-charcoal/25 text-pe-charcoal/65 hover:border-pe-gold hover:text-pe-gold transition-colors duration-200"
      >
        {locale === 'es' ? 'Ver productos' : 'View products'}
      </a>
    </div>
  );
}
