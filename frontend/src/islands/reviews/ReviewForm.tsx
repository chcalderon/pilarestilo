import { useEffect, useState } from 'react';
import { Star } from 'lucide-react';
import { createReview, getProductReviews } from '../../lib/api';

interface Props {
  readonly productId: string;
  readonly token?: string;
  readonly userId?: string;
  readonly locale?: string;
  readonly onSubmitted?: () => void;
}

function LoginPrompt({ locale, loginHref }: { readonly locale: string; readonly loginHref: string }) {
  const label = locale === 'es' ? 'Inicia sesión' : 'Sign in';
  const suffix = locale === 'es' ? 'para escribir una reseña.' : 'to write a review.';
  return (
    <div className="py-6 border-t border-pe-black/10">
      <p className="text-pe-charcoal/60 text-sm">
        <a href={loginHref} className="text-pe-rose-ink hover:underline">{label}</a>{' '}{suffix}
      </p>
    </div>
  );
}

function successTitleFor(locale: string, replacing: boolean): string {
  if (replacing) return locale === 'es' ? '¡Gracias! Actualizamos tu reseña.' : 'Thanks! Your review was updated.';
  return locale === 'es' ? '¡Gracias por tu reseña!' : 'Thank you for your review!';
}

function SuccessMessage({ locale, replacing }: { readonly locale: string; readonly replacing: boolean }) {
  return (
    <div className="py-6 border-t border-pe-black/10">
      <p className="text-pe-rose-ink font-display text-lg">
        {successTitleFor(locale, replacing)}
      </p>
      <p className="text-pe-charcoal/50 text-sm mt-1">
        {locale === 'es' ? 'Será visible una vez aprobada.' : 'It will be visible once approved.'}
      </p>
    </div>
  );
}

function formTitleFor(locale: string, replacing: boolean): string {
  if (replacing) return locale === 'es' ? 'Actualizar tu reseña' : 'Update your review';
  return locale === 'es' ? 'Escribir una reseña' : 'Write a review';
}

function submitLabelFor(locale: string, submitting: boolean, replacing: boolean): string {
  if (submitting) return locale === 'es' ? 'Enviando...' : 'Submitting...';
  if (replacing) return locale === 'es' ? 'Reemplazar reseña' : 'Replace review';
  return locale === 'es' ? 'Publicar reseña' : 'Submit review';
}

interface StarRatingProps {
  readonly display: number;
  readonly onRate: (i: number) => void;
  readonly onHover: (i: number) => void;
}

function StarRating({ display, onRate, onHover }: StarRatingProps) {
  return (
    <div className="flex gap-1">
      {[1, 2, 3, 4, 5].map(i => (
        <button
          key={i}
          type="button"
          onClick={() => onRate(i)}
          onMouseEnter={() => onHover(i)}
          onMouseLeave={() => onHover(0)}
          className="p-1"
        >
          <Star
            size={20}
            strokeWidth={1.25}
            className={`transition-colors ${i <= display ? 'fill-pe-rose stroke-pe-rose' : 'stroke-pe-charcoal/30 fill-none'}`}
          />
        </button>
      ))}
    </div>
  );
}

export default function ReviewForm({ productId, token, userId, locale = 'es', onSubmitted }: Props) {
  const [rating, setRating] = useState(0);
  const [hovered, setHovered] = useState(0);
  const [title, setTitle] = useState('');
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  // Whether this customer already has a live review here. A second one replaces it rather than
  // being refused, so the form loads what they wrote and says plainly that it is being replaced —
  // offering "Escribir una reseña" to somebody who already wrote one hid what the button did.
  const [replacing, setReplacing] = useState(false);

  useEffect(() => {
    if (!token || !userId) return;
    let cancelled = false;
    void getProductReviews(productId, token).then((reviews) => {
      const mine = reviews.find((review) => review.userId === userId);
      if (cancelled || !mine) return;
      setReplacing(true);
      setRating(mine.rating);
      setTitle(mine.title ?? '');
      setComment(mine.comment ?? '');
    });
    return () => { cancelled = true; };
  }, [productId, token, userId]);
  const redirectPath = typeof window !== 'undefined' ? window.location.pathname : `/${locale}/products/${productId}`;
  const loginHref = `/${locale}/auth/login?redirect=${encodeURIComponent(redirectPath)}`;

  if (!token) {
    return <LoginPrompt locale={locale} loginHref={loginHref} />;
  }

  if (success) {
    return <SuccessMessage locale={locale} replacing={replacing} />;
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
    } catch {
      // No duplicate branch here: a second review now replaces the first rather than being
      // refused, so the only failure left is the request itself.
      setError(locale === 'es' ? 'Error al enviar. Intenta de nuevo.' : 'Error submitting. Try again.');
    } finally {
      setSubmitting(false);
    }
  };

  const display = hovered || rating;
  const formTitle = formTitleFor(locale, replacing);
  const submitLabel = submitLabelFor(locale, submitting, replacing);

  return (
    <form onSubmit={handleSubmit} className="py-6 border-t border-pe-black/10 space-y-5">
      <h3 className="font-display text-xl text-pe-black">
        {formTitle}
      </h3>

      {/* Star rating */}
      <div>
        <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/60 mb-2">
          {locale === 'es' ? 'Puntuación' : 'Rating'}
        </p>
        <StarRating display={display} onRate={setRating} onHover={setHovered} />
      </div>

      {/* Title */}
      <div>
        <label className="block text-[10px] tracking-widest uppercase text-pe-charcoal/60 mb-1">
          {locale === 'es' ? 'Título (opcional)' : 'Title (optional)'}
        </label>
        <input
          type="text"
          value={title}
          onChange={e => setTitle(e.target.value)}
          maxLength={100}
          className="w-full border border-pe-black/15 bg-transparent px-3 py-2 text-sm text-pe-black focus:outline-hidden focus:border-pe-rose transition-colors"
        />
      </div>

      {/* Comment */}
      <div>
        <label className="block text-[10px] tracking-widest uppercase text-pe-charcoal/60 mb-1">
          {locale === 'es' ? 'Comentario' : 'Comment'} *
        </label>
        <textarea
          value={comment}
          onChange={e => setComment(e.target.value)}
          maxLength={1000}
          rows={4}
          className="w-full border border-pe-black/15 bg-transparent px-3 py-2 text-sm text-pe-black focus:outline-hidden focus:border-pe-rose transition-colors resize-none"
        />
        <p className="text-[10px] text-pe-charcoal/40 text-right mt-1">{comment.length}/1000</p>
      </div>

      {error && <p className="text-red-500 text-sm">{error}</p>}

      <button
        type="submit"
        disabled={submitting}
        className="bg-pe-black text-pe-offwhite px-8 py-3 text-xs tracking-widest uppercase hover:bg-pe-rose transition-colors disabled:opacity-50"
      >
        {submitLabel}
      </button>
    </form>
  );
}
