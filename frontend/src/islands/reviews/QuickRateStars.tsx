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

  return (
    <div className="inline-flex items-center gap-0.5" role="group" aria-label={groupLabel}>
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
            className={value <= display ? 'fill-[#B76E79] stroke-[#B76E79]' : 'fill-none stroke-[#3A3A3A]/35'}
          />
        </button>
      ))}
    </div>
  );
}
