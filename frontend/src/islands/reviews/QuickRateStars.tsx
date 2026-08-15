import { useState } from 'react';
import { Star } from 'lucide-react';
import { createReview } from '../../lib/api';

interface Props {
  productId: string;
  token?: string;
  locale?: string;
}

export default function QuickRateStars({ productId, token, locale = 'es' }: Props) {
  const [rating, setRating] = useState(0);
  const [hover, setHover] = useState(0);
  const [saving, setSaving] = useState(false);
  const [locked, setLocked] = useState(false);

  if (!token) {
    return null;
  }

  const display = hover || rating;
  const disabled = saving || locked;

  async function handleRate(value: number) {
    if (!token || disabled) return;
    setSaving(true);
    setRating(value);
    try {
      await createReview(productId, token, { rating: value });
      setLocked(true);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message.toLowerCase() : '';
      if (message.includes('already')) {
        setLocked(true);
      } else {
        setRating(0);
      }
    } finally {
      setSaving(false);
    }
  }

  const groupLabel = locale === 'es' ? 'Valorar producto' : 'Rate product';

  const doneLabel = locale === 'es' ? '¡Gracias!' : 'Thanks!';

  return (
    /*
     * Sits inline beside the average, not stacked under it. Two unlabelled rows of stars read as
     * one broken control — the customer cannot tell which is the product's score and which is
     * their own vote.
     */
    <div className="inline-flex items-center gap-1" role="group" aria-label={groupLabel}>
      <span className="font-sans text-[0.6rem] tracking-wider uppercase text-pe-charcoal/50">
        {locked ? doneLabel : (locale === 'es' ? 'Valorar' : 'Rate')}
      </span>
      {[1, 2, 3, 4, 5].map((value) => (
        <button
          key={value}
          type="button"
          onClick={() => { void handleRate(value); }}
          onMouseEnter={() => { if (!disabled) setHover(value); }}
          onMouseLeave={() => { if (!disabled) setHover(0); }}
          disabled={disabled}
          aria-label={`${locale === 'es' ? 'Calificar con' : 'Rate'} ${value} ${locale === 'es' ? 'estrellas' : 'stars'}`}
          className="p-0.5 disabled:cursor-default"
        >
          <Star
            size={13}
            strokeWidth={1.35}
            /* Token, not a literal: the hardcoded grey was invisible on the dark storefront. */
            className={
              value <= display
                ? 'fill-pe-rose stroke-pe-rose'
                : 'fill-none stroke-pe-charcoal/40'
            }
          />
        </button>
      ))}
    </div>
  );
}
