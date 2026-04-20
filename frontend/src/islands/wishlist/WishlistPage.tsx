import { useEffect, useState } from 'react';
import { ShoppingBag, X } from 'lucide-react';
import { useWishlistStore } from '../../lib/wishlistStore';
import { getProduct } from '../../lib/api';
import type { ProductDto } from '../../lib/api';
import { useCartStore } from '../../lib/cartStore';

interface Props {
  locale?: string;
  token?: string;
}

export default function WishlistPage({ locale = 'es', token }: Props) {
  const { productIds, remove, syncFromServer } = useWishlistStore();
  const addToCart = useCartStore(s => s.addItem);
  const [products, setProducts] = useState<ProductDto[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (token) syncFromServer(token);
  }, [token]);

  useEffect(() => {
    const ids = [...productIds];
    if (!ids.length) { setLoading(false); setProducts([]); return; }
    setLoading(true);
    Promise.all(ids.map(id => getProduct(id).catch(() => null)))
      .then(results => setProducts(results.filter(Boolean) as ProductDto[]))
      .finally(() => setLoading(false));
  }, [productIds]);

  const formatPrice = (p: ProductDto) =>
    new Intl.NumberFormat('es-CL', { style: 'currency', currency: p.price.currency ?? 'CLP', maximumFractionDigits: 0 }).format(p.price.amount);

  if (loading) {
    return (
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
        {[...Array(4)].map((_, i) => (
          <div key={i} className="aspect-[3/4] bg-[#EDE3D8] animate-pulse" />
        ))}
      </div>
    );
  }

  if (!products.length) {
    return (
      <div className="text-center py-24">
        <img src="/ornaments/seal-pe.svg" alt="" className="w-24 h-24 mx-auto mb-6 opacity-20" />
        <p className="font-['Cormorant_Garamond',serif] text-2xl text-[#3A3A3A] mb-4">
          {locale === 'es' ? 'Tu lista de favoritos está vacía' : 'Your wishlist is empty'}
        </p>
        <a
          href={`/${locale}/products`}
          className="inline-flex items-center gap-2 bg-[#B76E79] text-[#F8F4EF] px-6 py-3 text-sm tracking-widest uppercase hover:bg-[#8E4F58] transition-colors"
        >
          {locale === 'es' ? 'Explorar productos' : 'Explore products'}
        </a>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
      {products.map(p => (
        <div key={p.id} className="group relative">
          <button
            onClick={() => remove(p.id, token)}
            className="absolute top-3 right-3 z-10 w-8 h-8 flex items-center justify-center bg-white/80 hover:bg-white transition-colors"
            aria-label="Quitar de favoritos"
          >
            <X size={14} strokeWidth={1.5} className="text-[#1A1A1A]" />
          </button>

          <a href={`/${locale}/products/${p.id}`} className="block">
            <div className="aspect-[3/4] overflow-hidden bg-[#EDE3D8] mb-3">
              <img
                src={p.imageUrl}
                alt={p.name}
                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
              />
            </div>
            <p className="text-[10px] tracking-widest uppercase text-[#3A3A3A]/60 mb-1">{p.brand}</p>
            <p className="font-['Cormorant_Garamond',serif] text-lg leading-tight text-[#1A1A1A] mb-1">{p.name}</p>
            <p className="font-['Cormorant_Garamond',serif] text-[#B76E79]">{formatPrice(p)}</p>
          </a>

          <button
            onClick={() => { addToCart({ id: p.id, name: p.name, brand: p.brand, price: p.price, imageUrl: p.imageUrl, condition: p.condition }); }}
            className="mt-3 w-full flex items-center justify-center gap-2 border border-[#1A1A1A] py-2.5 text-xs tracking-widest uppercase hover:bg-[#1A1A1A] hover:text-[#F8F4EF] transition-colors"
          >
            <ShoppingBag size={14} strokeWidth={1.25} />
            {locale === 'es' ? 'Mover al carrito' : 'Move to cart'}
          </button>
        </div>
      ))}
    </div>
  );
}
