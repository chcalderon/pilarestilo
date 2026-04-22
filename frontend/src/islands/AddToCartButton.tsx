import { useState } from 'react';
import { useCartStore } from '../lib/cartStore';
import type { Locale } from '../i18n/index';

interface Props {
  productId: string;
  name: string;
  brand: string;
  price: { amount: number; currency: string };
  imageUrl: string;
  condition: 'NEW' | 'USED';
  stock: number;
  locale: Locale;
}

export default function AddToCartButton({
  productId,
  name,
  brand,
  price,
  imageUrl,
  condition,
  stock,
  locale,
}: Props) {
  const addItem = useCartStore((s) => s.addItem);
  const [added, setAdded] = useState(false);

  const outOfStock = stock === 0;

  const labels = {
    addToCart: locale === 'es' ? 'Agregar al Carrito' : 'Add to Cart',
    outOfStock: locale === 'es' ? 'Sin Stock' : 'Out of Stock',
    added: locale === 'es' ? '¡Agregado!' : 'Added!',
  };

  function handleClick() {
    if (outOfStock || added) return;
    addItem({ id: productId, productId, name, brand, price, imageUrl, condition });
    setAdded(true);
    setTimeout(() => setAdded(false), 1800);
  }

  return (
    <button
      onClick={handleClick}
      disabled={outOfStock}
      aria-label={outOfStock ? labels.outOfStock : labels.addToCart}
      className={[
        'w-full font-sans text-xs tracking-widest uppercase px-4 py-2.5 transition-all duration-200',
        'focus:outline-none focus-visible:ring-2 focus-visible:ring-pe-gold focus-visible:ring-offset-1',
        outOfStock
          ? 'bg-pe-black/10 text-pe-black/30 cursor-not-allowed'
          : added
          ? 'bg-pe-gold/80 text-pe-black'
          : 'bg-pe-gold text-pe-black hover:bg-opacity-90 active:scale-95',
      ].join(' ')}
    >
      {outOfStock ? labels.outOfStock : added ? labels.added : labels.addToCart}
    </button>
  );
}
