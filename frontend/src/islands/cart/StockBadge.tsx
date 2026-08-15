import { AlertTriangle, PackageX, Ruler } from 'lucide-react';
import type { StockIssue } from '../../lib/useStockCheck';
import type { Locale } from '../../i18n/index';

interface Props {
  issue: StockIssue;
  locale: Locale;
}

const copy = {
  es: { soldOut: 'Sin stock', short: 'Solo quedan', needsVariant: 'Falta elegir talla' },
  en: { soldOut: 'Out of stock', short: 'Only', needsVariant: 'Choose a size' },
} as const;

export function stockIssueLabel(issue: StockIssue, locale: Locale): string {
  const l = copy[locale === 'es' ? 'es' : 'en'];
  if (issue.type === 'SOLD_OUT') return l.soldOut;
  if (issue.type === 'NEEDS_VARIANT') return l.needsVariant;
  return locale === 'es'
    ? `${l.short} ${issue.availableQty}`
    : `${l.short} ${issue.availableQty} left`;
}

/**
 * The unavailability marker, used by the cart and the checkout review step.
 *
 * <p>Filled rather than tinted text: a small coloured caption reads as a note, and this has to
 * read as a blocker at a glance. Every variant carries an icon and words, never colour alone.
 *
 * <p>The two states get different weight on purpose. SOLD_OUT is final — the only move is to
 * remove the line — so it is the loudest thing on the row. SHORT is a number the customer can
 * fix by lowering the quantity, so it warns without pretending the item is unbuyable.
 */
export default function StockBadge({ issue, locale }: Props) {
  /* Both are blockers — the line cannot be ordered as it stands — so both get the loud fill. */
  const blocking = issue.type === 'SOLD_OUT' || issue.type === 'NEEDS_VARIANT';
  const Icon = issue.type === 'NEEDS_VARIANT'
    ? Ruler
    : issue.type === 'SOLD_OUT'
      ? PackageX
      : AlertTriangle;

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2 py-1 font-sans text-[0.66rem]
        tracking-[0.14em] uppercase whitespace-nowrap ${
          blocking
            ? 'bg-[#8f2d3b] text-white font-semibold'
            : 'bg-[#ffe9ec] text-[#8f2d3b] border border-[#cb6070]/50'
        }`}
    >
      <Icon size={12} strokeWidth={2.5} aria-hidden="true" />
      {stockIssueLabel(issue, locale)}
    </span>
  );
}

/**
 * Greys out the thumbnail of a line that cannot be bought at all.
 *
 * <p>The image is the first thing scanned on a cart row, so leaving it fully saturated is what
 * made a sold-out line still look purchasable. Nothing is applied for SHORT — that item is real
 * and available, just not in the quantity asked for.
 */
export function stockImageClass(issue: StockIssue | undefined): string {
  /* NEEDS_VARIANT is not dimmed: the product is there and buyable, the line just names no size. */
  return issue?.type === 'SOLD_OUT' ? 'grayscale opacity-40' : '';
}
