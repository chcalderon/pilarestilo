import type { Locale } from '../i18n/index';

/**
 * CLP is shown without decimals — the peso has no subunit, and a trailing ",00" reads as an
 * error to a Chilean customer. Amounts reaching here are already whole pesos; see
 * `checkoutTotals`.
 */
export function formatPrice(amount: number, currency = 'CLP', locale: Locale = 'es'): string {
  return new Intl.NumberFormat(locale === 'es' ? 'es-CL' : 'en-US', {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(amount);
}
