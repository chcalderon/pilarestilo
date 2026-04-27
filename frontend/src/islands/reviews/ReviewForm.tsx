import { useState } from 'react';
import { Star } from 'lucide-react';
import { createReview } from '../../lib/api';

interface Props {
  productId: string;
  token?: string;
  userId?: string;
  locale?: string;
  onSubmitted?: () => void;
}

export default function ReviewForm({ productId, token, userId, locale = 'es', onSubmitted }: Props) {
  const [rating, setRating] = useState(0);
  const [hovered, setHovered] = useState(0);
  const [title, setTitle] = useState('');
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const redirectPath = typeof window !== 'undefined' ? window.location.pathname : `/${locale}/products/${productId}`;
  const loginHref = `/${locale}/auth/login?redirect=${encodeURIComponent(redirectPath)}`;

  if (!token) {
    return (
      <div className="py-6 border-t border-[#EDE3D8]">
        <p className="text-[#3A3A3A]/60 text-sm">
          {locale === 'es' ? (
            <>
              <a href={loginHref} className="text-[#B76E79] hover:underline">
                Inicia sesión
              </a>
              {' '}para escribir una reseña.
            </>
          ) : (
            <>
              <a href={loginHref} className="text-[#B76E79] hover:underline">
                Sign in
              </a>
              {' '}to write a review.
            </>
          )}
        </p>
      </div>
    );
  }

  if (success) {
    return (
      <div className="py-6 border-t border-[#EDE3D8]">
        <p className="text-[#B76E79] font-['Cormorant_Garamond',serif] text-lg">
          {locale === 'es' ? '¡Gracias por tu reseña!' : 'Thank you for your review!'}
        </p>
        <p className="text-[#3A3A3A]/50 text-sm mt-1">
          {locale === 'es' ? 'Será visible una vez aprobada.' : 'It will be visible once approved.'}
        </p>
      </div>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (rating === 0) { setError(locale === 'es' ? 'Selecciona una puntuación' : 'Select a rating'); return; }
    if (!comment.trim()) { setError(locale === 'es' ? 'El comentario es obligatorio' : 'Comment is required'); return; }
    setSubmitting(true);
    setError('');
    try {
      await createReview(productId, token, { rating, title: title.trim() || undefined, comment: comment.trim() });
      setSuccess(true);
      onSubmitted?.();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '';
      if (msg.includes('409') || msg.includes('already')) {
        setError(locale === 'es' ? 'Ya reseñaste este producto.' : 'You already reviewed this product.');
      } else {
        setError(locale === 'es' ? 'Error al enviar. Intenta de nuevo.' : 'Error submitting. Try again.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const display = hovered || rating;

  return (
    <form onSubmit={handleSubmit} className="py-6 border-t border-[#EDE3D8] space-y-5">
      <h3 className="font-['Cormorant_Garamond',serif] text-xl text-[#1A1A1A]">
        {locale === 'es' ? 'Escribir una reseña' : 'Write a review'}
      </h3>

      {/* Star rating */}
      <div>
        <p className="text-[10px] tracking-widest uppercase text-[#3A3A3A]/60 mb-2">
          {locale === 'es' ? 'Puntuación' : 'Rating'}
        </p>
        <div className="flex gap-1">
          {[1, 2, 3, 4, 5].map(i => (
            <button
              key={i}
              type="button"
              onClick={() => setRating(i)}
              onMouseEnter={() => setHovered(i)}
              onMouseLeave={() => setHovered(0)}
              className="p-1"
            >
              <Star
                size={20}
                strokeWidth={1.25}
                className={`transition-colors ${i <= display ? 'fill-[#B76E79] stroke-[#B76E79]' : 'stroke-[#3A3A3A]/30 fill-none'}`}
              />
            </button>
          ))}
        </div>
      </div>

      {/* Title */}
      <div>
        <label className="block text-[10px] tracking-widest uppercase text-[#3A3A3A]/60 mb-1">
          {locale === 'es' ? 'Título (opcional)' : 'Title (optional)'}
        </label>
        <input
          type="text"
          value={title}
          onChange={e => setTitle(e.target.value)}
          maxLength={100}
          className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm text-[#1A1A1A] focus:outline-none focus:border-[#B76E79] transition-colors"
        />
      </div>

      {/* Comment */}
      <div>
        <label className="block text-[10px] tracking-widest uppercase text-[#3A3A3A]/60 mb-1">
          {locale === 'es' ? 'Comentario' : 'Comment'} *
        </label>
        <textarea
          value={comment}
          onChange={e => setComment(e.target.value)}
          maxLength={1000}
          rows={4}
          className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm text-[#1A1A1A] focus:outline-none focus:border-[#B76E79] transition-colors resize-none"
        />
        <p className="text-[10px] text-[#3A3A3A]/40 text-right mt-1">{comment.length}/1000</p>
      </div>

      {error && <p className="text-red-500 text-sm">{error}</p>}

      <button
        type="submit"
        disabled={submitting}
        className="bg-[#1A1A1A] text-[#F8F4EF] px-8 py-3 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50"
      >
        {submitting
          ? (locale === 'es' ? 'Enviando...' : 'Submitting...')
          : (locale === 'es' ? 'Publicar reseña' : 'Submit review')}
      </button>
    </form>
  );
}
