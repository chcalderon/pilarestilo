import { Heart } from 'lucide-react';
import { useWishlistStore } from '../../lib/wishlistStore';
import { useEffect, useState } from 'react';

interface Props {
  productId: string;
  token?: string;
  className?: string;
  showLabel?: boolean;
  locale?: 'es' | 'en';
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
            ? 'fill-[#B76E79] stroke-[#B76E79]'
            : 'stroke-current group-hover:stroke-[#B76E79]'
        }`}
      />
      {showLabel && (
        <span className={`font-sans text-xs tracking-widest uppercase transition-colors duration-200 ${
          inWishlist ? 'text-[#B76E79]' : 'group-hover:text-[#B76E79]'
        }`}>
          {inWishlist ? labelRemove : labelAdd}
        </span>
      )}
    </button>
  );
}
