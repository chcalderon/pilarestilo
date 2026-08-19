import { useEffect, useState } from 'react';
import { Star } from 'lucide-react';
import { createReview } from '../../lib/api';

interface Props {
  productId: string;
  token?: string;
  locale?: string;
}

/**
 * Which products this browser has already rated, and with what.
 *
 * <p>The backend refuses a second review, but nothing told the card, so a reload offered
 * "Valorar" again and the click would have failed. There is no endpoint for "my review" and
 * asking for a product's whole review list per card would be one request per tile, so the
 * answer is remembered here. The server stays the authority; this only stops the card offering
 * something that cannot work.
 */
const RATED_KEY = 'pe-rated-products';

function readRated(): Record<string, number> {
  if (typeof window === 'undefined') return {};
  try {
    const raw = window.localStorage.getItem(RATED_KEY);
    const parsed = raw ? JSON.parse(raw) : {};
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function rememberRated(productId: string, value: number) {
  try {
    window.localStorage.setItem(RATED_KEY, JSON.stringify({ ...readRated(), [productId]: value }));
  } catch {
    /* Private mode or a full quota: the rating still reached the server, which is what counts. */
  }
}

export default function QuickRateStars({ productId, token, locale = 'es' }: Props) {
  const [rating, setRating] = useState(0);
  const [hover, setHover] = useState(0);
  const [saving, setSaving] = useState(false);
  const [locked, setLocked] = useState(false);
  /** Collapsed until asked for: five idle stars beside the average read as one broken control. */
  const [open, setOpen] = useState(false);
  /** Thanks is a moment, not a state. It gives way to the rating the customer left. */
  const [justRated, setJustRated] = useState(false);

  useEffect(() => {
    const mine = readRated()[productId];
    if (mine) {
      setRating(mine);
      setLocked(true);
    }
  }, [productId]);

  useEffect(() => {
    if (!justRated) return;
    const timer = window.setTimeout(() => setJustRated(false), 4000);
    return () => window.clearTimeout(timer);
  }, [justRated]);

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
      rememberRated(productId, value);
      setLocked(true);
      setJustRated(true);
    } catch {
      // Rating again is allowed now, so a failure here is only ever the request. Put the stars
      // back the way they were rather than leaving a vote that was never recorded.
      setRating(0);
    } finally {
      setSaving(false);
    }
  }

  const groupLabel = locale === 'es' ? 'Valorar producto' : 'Rate product';

  const doneLabel = locale === 'es' ? '¡Gracias!' : 'Thanks!';
  const openLabel = locale === 'es' ? 'Valorar' : 'Rate';

  if (locked) {
    /* Four seconds of acknowledgement, then the vote itself — which is the lasting fact. */
    if (justRated) {
      return (
        <span
          role="status"
          className="font-sans text-[0.6rem] tracking-wider uppercase text-pe-rose"
        >
          {doneLabel}
        </span>
      );
    }
    return (
      <span
        className="inline-flex items-center gap-0.5"
        title={locale === 'es' ? 'Tu valoración' : 'Your rating'}
      >
        {[1, 2, 3, 4, 5].map((value) => (
          <Star
            key={value}
            size={13}
            strokeWidth={1.35}
            aria-hidden="true"
            className={
              value <= rating ? 'fill-pe-rose stroke-pe-rose' : 'fill-none stroke-pe-charcoal/40'
            }
          />
        ))}
        <span className="sr-only">
          {locale === 'es' ? `Tu valoración: ${rating} de 5` : `Your rating: ${rating} of 5`}
        </span>
      </span>
    );
  }

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="font-sans text-[0.6rem] tracking-wider uppercase text-pe-muted
          underline underline-offset-2 hover:text-pe-rose transition-colors
          focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose"
      >
        {openLabel}
      </button>
    );
  }

  return (
    /*
     * Sits inline beside the average, not stacked under it. Two unlabelled rows of stars read as
     * one broken control — the customer cannot tell which is the product's score and which is
     * their own vote.
     */
    <div className="inline-flex items-center gap-1" role="group" aria-label={groupLabel}>
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
