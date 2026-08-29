import { Heart } from 'lucide-react';
import { useWishlistStore } from '../../lib/wishlistStore';
import { useEffect, useState } from 'react';

interface Props {
  readonly productId: string;
  readonly token?: string;
  readonly className?: string;
  readonly showLabel?: boolean;
  readonly locale?: 'es' | 'en';
}

export default function WishlistButton({ productId, token, className = '', showLabel = false, locale = 'es' }: Props) {
  const { has, toggle } = useWishlistStore();
  const [mounted, setMounted] = useState(false);

  useEffect(() => { setMounted(true); }, []);

  const inWishlist = mounted && has(productId);

  const labelAdd = locale === 'es' ? 'Agregar a favoritos' : 'Save to wishlist';
  const labelRemove = locale === 'es' ? 'En favoritos' : 'Saved';

  return (
    <button
      type="button"
      onClick={(event) => {
        event.preventDefault();
        event.stopPropagation();
        toggle(productId, token);
      }}
      aria-label={inWishlist ? labelRemove : labelAdd}
      className={`group flex items-center justify-center gap-2 transition-all duration-200 ${className}`}
    >
      <Heart
        size={showLabel ? 16 : 20}
        strokeWidth={1.25}
        className={`flex-shrink-0 transition-all duration-200 ${
          inWishlist
            ? 'fill-pe-rose stroke-pe-rose'
            : 'stroke-current group-hover:stroke-pe-rose'
        }`}
      />
      {showLabel && (
        <span className={`font-sans text-xs tracking-widest uppercase transition-colors duration-200 ${
          inWishlist ? 'text-pe-rose-ink' : 'group-hover:text-pe-rose-ink'
        }`}>
          {inWishlist ? labelRemove : labelAdd}
        </span>
      )}
    </button>
  );
}
