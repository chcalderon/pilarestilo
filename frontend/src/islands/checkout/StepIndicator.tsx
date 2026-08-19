import { Check } from 'lucide-react';
import { CHECKOUT_STEPS, stepIndex, type CheckoutStep } from '../../lib/checkoutStore';
import type { Locale } from '../../i18n/index';

interface Props {
  locale: Locale;
  current: CheckoutStep;
  /** How far the customer actually got. Steps beyond it are not reachable yet. */
  furthest: CheckoutStep;
  onSelect: (step: CheckoutStep) => void;
}

const STEP_LABELS: Record<CheckoutStep, { es: string; en: string }> = {
  shipping: { es: 'Envío', en: 'Shipping' },
  payment: { es: 'Pago', en: 'Payment' },
  review: { es: 'Resumen', en: 'Review' },
};

export default function StepIndicator({ locale, current, furthest, onSelect }: Props) {
  const currentIdx = stepIndex(current);
  const furthestIdx = stepIndex(furthest);

  return (
    <nav aria-label={locale === 'es' ? 'Progreso de la compra' : 'Checkout progress'}>
      <ol className="flex items-stretch gap-1 sm:gap-2">
        {CHECKOUT_STEPS.map((step, idx) => {
          const isCurrent = idx === currentIdx;
          const isDone = idx < currentIdx;
          /* Going back to change an answer is always allowed; skipping past an unanswered step is not. */
          const reachable = idx <= furthestIdx;
          const label = STEP_LABELS[step][locale === 'es' ? 'es' : 'en'];

          return (
            <li key={step} className="flex-1">
              <button
                type="button"
                onClick={() => reachable && !isCurrent && onSelect(step)}
                disabled={!reachable || isCurrent}
                aria-current={isCurrent ? 'step' : undefined}
                /*
                 * `min-h-11` is 44px — the touch-target floor. The layout is identical on
                 * every width: only the type scale changes, so the flow reads the same on a
                 * phone as on a desktop.
                 */
                className={`w-full min-h-11 flex items-center justify-center gap-2 px-2 sm:px-4 py-3
                  border-b-2 transition-colors duration-200
                  focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose focus-visible:ring-offset-2
                  ${
                    isCurrent
                      ? 'border-pe-black text-pe-black'
                      : isDone || reachable
                        ? 'border-pe-charcoal/25 text-pe-charcoal/75 hover:text-pe-black hover:border-pe-charcoal/50 cursor-pointer'
                        : 'border-pe-charcoal/15 text-pe-charcoal/40 cursor-not-allowed'
                  }`}
              >
                <span
                  aria-hidden="true"
                  className={`shrink-0 w-6 h-6 flex items-center justify-center text-[0.7rem] font-sans
                    ${
                      isDone
                        ? 'bg-pe-black text-pe-white'
                        : isCurrent
                          ? 'border border-pe-black text-pe-black'
                          : 'border border-pe-charcoal/30 text-pe-charcoal/50'
                    }`}
                >
                  {isDone ? <Check size={13} strokeWidth={2.5} /> : idx + 1}
                </span>
                <span
                  className={`font-sans text-[0.7rem] sm:text-xs tracking-[0.14em] uppercase truncate ${
                    isCurrent ? 'font-semibold' : ''
                  }`}
                >
                  {label}
                </span>
                {/* Conveys "done" to screen readers, which cannot see the tick. */}
                {isDone && (
                  <span className="sr-only">
                    {locale === 'es' ? '(completado)' : '(completed)'}
                  </span>
                )}
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
