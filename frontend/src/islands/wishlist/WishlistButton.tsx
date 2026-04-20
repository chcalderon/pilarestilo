import { Heart } from 'lucide-react';
import { useWishlistStore } from '../../lib/wishlistStore';
import { useEffect, useState } from 'react';

interface Props {
  productId: string;
  token?: string;
  className?: string;
}

export default function WishlistButton({ productId, token, className = '' }: Props) {
  const { has, toggle } = useWishlistStore();
  const [mounted, setMounted] = useState(false);

  useEffect(() => { setMounted(true); }, []);

  const inWishlist = mounted && has(productId);

  return (
    <button
      onClick={() => toggle(productId, token)}
      aria-label={inWishlist ? 'Quitar de favoritos' : 'Agregar a favoritos'}
      className={`group flex items-center justify-center transition-all duration-200 ${className}`}
    >
      <Heart
        size={20}
        strokeWidth={1.25}
        className={`transition-all duration-200 ${
          inWishlist
            ? 'fill-[#B76E79] stroke-[#B76E79]'
            : 'stroke-current group-hover:stroke-[#B76E79]'
        }`}
      />
    </button>
  );
}
