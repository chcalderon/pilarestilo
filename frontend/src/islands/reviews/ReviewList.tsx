import { useEffect, useState } from 'react';
import { Star } from 'lucide-react';
import { getProductReviews } from '../../lib/api';
import type { ReviewDto } from '../../lib/api';

const SKELETON_ROWS = ['sk-1', 'sk-2', 'sk-3'];

interface Props {
  readonly productId: string;
  readonly locale?: string;
  /** Sent so the backend includes the reader's own review while it awaits approval. */
  readonly token?: string;
  /** Who is reading, so their own pending review stays visible to them and nobody else. */
  readonly userId?: string;
}

function Stars({ value }: { readonly value: number }) {
  return (
    <span className="flex gap-0.5">
      {[1, 2, 3, 4, 5].map(i => (
        <Star
          key={i}
          size={12}
          strokeWidth={1.25}
          className={i <= value ? 'fill-pe-rose stroke-pe-rose' : 'stroke-pe-charcoal/30 fill-none'}
        />
      ))}
    </span>
  );
}

export default function ReviewList({ productId, locale = 'es', token, userId }: Props) {
  const [reviews, setReviews] = useState<ReviewDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const PER_PAGE = 10;

  useEffect(() => {
    setLoading(true);
    getProductReviews(productId, token)
      .then(setReviews)
      .finally(() => setLoading(false));
  }, [productId, token]);

  // Everyone sees approved reviews. The reader also sees their own while it waits for a moderator,
  // labelled as pending — otherwise replacing a quick rating with a written one looks like the
  // review was swallowed.
  const visible = reviews.filter(r => r.approved || (userId != null && r.userId === userId));
  const paged = visible.slice(page * PER_PAGE, (page + 1) * PER_PAGE);
  const totalPages = Math.ceil(visible.length / PER_PAGE);

  if (loading) {
    return (
      <div className="space-y-4">
        {SKELETON_ROWS.map((key) => (
          <div key={key} className="animate-pulse">
            <div className="h-3 bg-pe-cream w-24 mb-2" />
            <div className="h-4 bg-pe-cream w-3/4 mb-1" />
            <div className="h-3 bg-pe-cream w-full" />
          </div>
        ))}
      </div>
    );
  }

  if (!visible.length) {
    return (
      <div className="py-10 text-center">
        <p className="font-display text-xl text-pe-charcoal/50">
          {locale === 'es' ? 'Sé el primero en escribir una reseña' : 'Be the first to write a review'}
        </p>
      </div>
    );
  }

  let reviewCountLabel: string;
  if (locale === 'es') {
    reviewCountLabel = visible.length === 1 ? 'reseña' : 'reseñas';
  } else {
    reviewCountLabel = visible.length === 1 ? 'review' : 'reviews';
  }

  return (
    <div>
      <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/50 mb-6">
        {visible.length} {reviewCountLabel}
      </p>
      <ul className="space-y-8">
        {paged.map(r => (
          <li key={r.id} className="border-b border-pe-black/10 pb-8 last:border-0">
            <div className="flex items-center gap-3 mb-2">
              <Stars value={r.rating} />
              {r.title && (
                <span className="font-display text-pe-black">{r.title}</span>
              )}
              {!r.approved && (
                <span className="text-[10px] tracking-widest uppercase text-pe-rose-ink border border-pe-rose/40 rounded-full px-2 py-0.5">
                  {locale === 'es' ? 'Pendiente de aprobación' : 'Awaiting approval'}
                </span>
              )}
            </div>
            {r.comment && (
              <p className="text-pe-charcoal text-sm leading-relaxed mb-3">{r.comment}</p>
            )}
            <p className="text-[10px] text-pe-charcoal/40 tracking-wide">
              {new Date(r.createdAt).toLocaleDateString(locale === 'es' ? 'es-CL' : 'en-US', {
                year: 'numeric', month: 'long', day: 'numeric'
              })}
            </p>
          </li>
        ))}
      </ul>

      {totalPages > 1 && (
        <div className="flex items-center gap-4 mt-8">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage(p => p - 1)}
            className="text-xs tracking-widest uppercase text-pe-rose-ink disabled:opacity-30 hover:text-pe-rose-deep transition-colors"
          >
            {locale === 'es' ? 'Anterior' : 'Previous'}
          </button>
          <span className="text-xs text-pe-charcoal/50">{page + 1} / {totalPages}</span>
          <button
            type="button"
            disabled={page === totalPages - 1}
            onClick={() => setPage(p => p + 1)}
            className="text-xs tracking-widest uppercase text-pe-rose-ink disabled:opacity-30 hover:text-pe-rose-deep transition-colors"
          >
            {locale === 'es' ? 'Siguiente' : 'Next'}
          </button>
        </div>
      )}
    </div>
  );
}
