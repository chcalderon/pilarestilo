import { useEffect, useState } from 'react';
import { ShoppingBag } from 'lucide-react';
import { getProduct, getSharedWishlist, type ProductDto } from '../../lib/api';
import { useCartStore } from '../../lib/cartStore';

interface Props {
  locale?: string;
  shareToken: string;
}

export default function SharedWishlistPage({ locale = 'es', shareToken }: Props) {
  const addToCart = useCartStore((s) => s.addItem);
  const [products, setProducts] = useState<ProductDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    setLoading(true);
    setNotFound(false);

    void getSharedWishlist(shareToken)
      .then((dto) => Promise.all(dto.productIds.map((id) => getProduct(id).catch(() => null))))
      .then((results) => {
        setProducts(results.filter(Boolean) as ProductDto[]);
      })
      .catch(() => {
        setProducts([]);
        setNotFound(true);
      })
      .finally(() => setLoading(false));
  }, [shareToken]);

  const formatPrice = (p: ProductDto) =>
    new Intl.NumberFormat('es-CL', {
      style: 'currency',
      currency: p.price.currency ?? 'CLP',
      maximumFractionDigits: 0,
    }).format(p.price.amount);

  const hasDiscount = (p: ProductDto) =>
    !!p.listPrice && p.listPrice.currency === p.price.currency && p.listPrice.amount > p.price.amount;

  const formatListPrice = (p: ProductDto) =>
    p.listPrice
      ? new Intl.NumberFormat('es-CL', {
          style: 'currency',
          currency: p.listPrice.currency ?? 'CLP',
          maximumFractionDigits: 0,
        }).format(p.listPrice.amount)
      : '';

  if (loading) {
    return (
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
        {[...Array(4)].map((_, i) => (
          <div key={i} className="aspect-[3/4] bg-[#DDCCB8] animate-pulse" />
        ))}
      </div>
    );
  }

  if (notFound) {
    return (
      <div className="text-center py-24">
        <p className="font-['Cormorant_Garamond',serif] text-3xl text-[#1A1A1A] mb-4">
          {locale === 'es' ? 'Link no disponible' : 'Link not available'}
        </p>
        <p className="text-sm text-[#6B5A4B]">
          {locale === 'es'
            ? 'Este wishlist compartido no existe o fue desactivado.'
            : 'This shared wishlist does not exist or has been disabled.'}
        </p>
      </div>
    );
  }

  if (!products.length) {
    return (
      <div className="text-center py-24">
        <p className="font-['Cormorant_Garamond',serif] text-3xl text-[#1A1A1A] mb-4">
          {locale === 'es' ? 'Sin productos compartidos' : 'No shared products'}
        </p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
      {products.map((p) => (
        <div key={p.id} className="pe-wishlist-card group relative">
          <a href={`/${locale}/products/${p.id}`} className="block">
            <div className="pe-wishlist-image-frame aspect-[3/4] overflow-hidden mb-3">
              <img
                src={p.imageUrl}
                alt={p.name}
                className="pe-wishlist-image w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
              />
            </div>
            <p className="pe-wishlist-brand text-[10px] tracking-widest uppercase mb-1">{p.brand}</p>
            <p className="pe-wishlist-name font-['Cormorant_Garamond',serif] text-lg leading-tight mb-1">{p.name}</p>
            {hasDiscount(p) && (
              <p className="pe-wishlist-price font-sans text-xs text-[#3A3A3A]/45 line-through">{formatListPrice(p)}</p>
            )}
            <p className="pe-wishlist-price font-['Cormorant_Garamond',serif]">{formatPrice(p)}</p>
          </a>

          <button
            onClick={() => {
              addToCart({
                id: p.id,
                name: p.name,
                brand: p.brand,
                price: p.price,
                imageUrl: p.imageUrl,
                condition: p.condition,
              });
            }}
            className="pe-wishlist-cart-btn mt-3 w-full flex items-center justify-center gap-2 py-2.5 text-xs tracking-widest uppercase transition-colors"
          >
            <ShoppingBag size={14} strokeWidth={1.25} />
            {locale === 'es' ? 'Agregar al carrito' : 'Add to cart'}
          </button>
        </div>
      ))}
    </div>
  );
}
