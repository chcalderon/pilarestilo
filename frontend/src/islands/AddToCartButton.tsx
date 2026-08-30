import { useState } from 'react';
import { useCartStore, verifyStockForItem } from '../lib/cartStore';
import StockUnavailableModal from './cart/StockUnavailableModal';
import type { Locale } from '../i18n/index';

interface Props {
  readonly productId: string;
  readonly name: string;
  readonly brand: string;
  readonly price: { amount: number; currency: string };
  readonly imageUrl: string;
  readonly condition: 'NEW' | 'USED';
  readonly stock: number;
  readonly locale: Locale;
  readonly variantColor?: string;
  readonly variantSize?: string;
  readonly variantLabel?: string;
}

interface StockModalState {
  productName: string;
  availableQty: number;
  requestedQty: number;
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
  variantColor,
  variantSize,
  variantLabel,
}: Props) {
  const addItem = useCartStore((s) => s.addItem);
  const items = useCartStore((s) => s.items);
  const [added, setAdded] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [stockModal, setStockModal] = useState<StockModalState | null>(null);

  const outOfStock = stock === 0;

  const cartItemId =
    variantColor || variantSize
      ? [productId, variantColor ?? '', variantSize ?? ''].join('::')
      : productId;

  const labels = {
    addToCart: locale === 'es' ? 'Agregar al Carrito' : 'Add to Cart',
    outOfStock: locale === 'es' ? 'Sin Stock' : 'Out of Stock',
    added: locale === 'es' ? '¡Agregado!' : 'Added!',
    verifying: locale === 'es' ? 'Verificando...' : 'Checking...',
  };

  async function handleClick() {
    if (outOfStock || added || verifying) return;

    const existingItem = items.find((i) => i.id === cartItemId);
    const currentQty = existingItem?.quantity ?? 0;

    setVerifying(true);
    try {
      const variant =
        variantColor || variantSize
          ? { color: variantColor, size: variantSize }
          : null;
      const result = await verifyStockForItem(productId, variant, currentQty + 1);
      if (!result.ok) {
        setStockModal({
          productName: result.productName,
          availableQty: result.reason === 'INSUFFICIENT' ? result.availableQty : 0,
          requestedQty: currentQty + 1,
        });
        return;
      }
    } finally {
      setVerifying(false);
    }

    addItem({
      id: cartItemId,
      productId,
      name,
      brand,
      price,
      imageUrl,
      condition,
      variantColor,
      variantSize,
      variantLabel,
    });
    setAdded(true);
    setTimeout(() => setAdded(false), 1800);
  }

  let buttonStateClass: string;
  let buttonLabel: string;
  if (outOfStock) {
    buttonStateClass = 'bg-pe-charcoal/10 text-pe-charcoal/40 cursor-not-allowed';
    buttonLabel = labels.outOfStock;
  } else if (verifying) {
    buttonStateClass = 'bg-pe-rose-action/50 text-pe-on-dark cursor-wait';
    buttonLabel = labels.verifying;
  } else if (added) {
    buttonStateClass = 'bg-pe-rose-deep text-pe-on-dark';
    buttonLabel = labels.added;
  } else {
    buttonStateClass = 'bg-pe-rose-action text-pe-on-dark hover:bg-pe-rose-deep active:scale-95';
    buttonLabel = labels.addToCart;
  }

  return (
    <>
      <button
        type="button"
        onClick={handleClick}
        disabled={outOfStock || verifying}
        className={[
          'w-full min-h-11 font-sans text-xs tracking-widest uppercase px-4 py-3',
          'transition-[background-color,transform] duration-200 motion-reduce:transition-none',
          'focus:outline-hidden focus-visible:ring-2 focus-visible:ring-pe-rose focus-visible:ring-offset-1',
          buttonStateClass,
        ].join(' ')}
      >
        {buttonLabel}
      </button>

      <span role="status" aria-live="polite" className="sr-only">
        {added ? labels.added : ''}
      </span>

      {stockModal && (
        <StockUnavailableModal
          open={true}
          productName={stockModal.productName}
          availableQty={stockModal.availableQty}
          requestedQty={stockModal.requestedQty}
          onClose={() => setStockModal(null)}
        />
      )}
    </>
  );
}
