import { useEffect, useState } from 'react';
import { Star } from 'lucide-react';
import { getProductReviews } from '../../lib/api';
import type { ReviewDto } from '../../lib/api';

interface Props {
  productId: string;
  locale?: string;
}

function Stars({ value }: { value: number }) {
  return (
    <span className="flex gap-0.5">
      {[1, 2, 3, 4, 5].map(i => (
        <Star
          key={i}
          size={12}
          strokeWidth={1.25}
          className={i <= value ? 'fill-[#B76E79] stroke-[#B76E79]' : 'stroke-[#3A3A3A]/30 fill-none'}
        />
      ))}
    </span>
  );
}

export default function ReviewList({ productId, locale = 'es' }: Props) {
  const [reviews, setReviews] = useState<ReviewDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const PER_PAGE = 10;

  useEffect(() => {
    setLoading(true);
    getProductReviews(productId)
      .then(setReviews)
      .finally(() => setLoading(false));
  }, [productId]);

  const approved = reviews.filter(r => r.approved);
  const paged = approved.slice(page * PER_PAGE, (page + 1) * PER_PAGE);
  const totalPages = Math.ceil(approved.length / PER_PAGE);

  if (loading) {
    return (
      <div className="space-y-4">
        {[...Array(3)].map((_, i) => (
          <div key={i} className="animate-pulse">
            <div className="h-3 bg-[#EDE3D8] w-24 mb-2" />
            <div className="h-4 bg-[#EDE3D8] w-3/4 mb-1" />
            <div className="h-3 bg-[#EDE3D8] w-full" />
          </div>
        ))}
      </div>
    );
  }

  if (!approved.length) {
    return (
      <div className="py-10 text-center">
        <p className="font-['Cormorant_Garamond',serif] text-xl text-[#3A3A3A]/50">
          {locale === 'es' ? 'Sé el primero en escribir una reseña' : 'Be the first to write a review'}
        </p>
      </div>
    );
  }

  return (
    <div>
      <p className="text-[10px] tracking-widest uppercase text-[#3A3A3A]/50 mb-6">
        {approved.length} {locale === 'es' ? (approved.length === 1 ? 'reseña' : 'reseñas') : (approved.length === 1 ? 'review' : 'reviews')}
      </p>
      <ul className="space-y-8">
        {paged.map(r => (
          <li key={r.id} className="border-b border-[#EDE3D8] pb-8 last:border-0">
            <div className="flex items-center gap-3 mb-2">
              <Stars value={r.rating} />
              {r.title && (
                <span className="font-['Cormorant_Garamond',serif] text-[#1A1A1A]">{r.title}</span>
              )}
            </div>
            {r.comment && (
              <p className="text-[#3A3A3A] text-sm leading-relaxed mb-3">{r.comment}</p>
            )}
            <p className="text-[10px] text-[#3A3A3A]/40 tracking-wide">
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
            disabled={page === 0}
            onClick={() => setPage(p => p - 1)}
            className="text-xs tracking-widest uppercase text-[#B76E79] disabled:opacity-30 hover:text-[#8E4F58] transition-colors"
          >
            {locale === 'es' ? 'Anterior' : 'Previous'}
          </button>
          <span className="text-xs text-[#3A3A3A]/50">{page + 1} / {totalPages}</span>
          <button
            disabled={page === totalPages - 1}
            onClick={() => setPage(p => p + 1)}
            className="text-xs tracking-widest uppercase text-[#B76E79] disabled:opacity-30 hover:text-[#8E4F58] transition-colors"
          >
            {locale === 'es' ? 'Siguiente' : 'Next'}
          </button>
        </div>
      )}
    </div>
  );
}
