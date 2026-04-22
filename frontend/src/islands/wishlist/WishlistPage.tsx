import { useEffect, useMemo, useState } from 'react';
import { ShoppingBag, X } from 'lucide-react';
import { useWishlistStore } from '../../lib/wishlistStore';
import {
  disableWishlistShareLink,
  enableWishlistShareLink,
  getProduct,
  getWishlistShareLink,
  type ProductDto,
  type WishlistShareLinkDto,
} from '../../lib/api';
import { useCartStore } from '../../lib/cartStore';

interface Props {
  locale?: string;
  token?: string;
}

export default function WishlistPage({ locale = 'es', token }: Props) {
  const { productIds, remove, syncFromServer } = useWishlistStore();
  const addToCart = useCartStore((s) => s.addItem);
  const [products, setProducts] = useState<ProductDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [shareLoading, setShareLoading] = useState(false);
  const [shareState, setShareState] = useState<WishlistShareLinkDto>({ token: null, enabled: false });
  const [shareMessage, setShareMessage] = useState<string | null>(null);

  useEffect(() => {
    if (token) {
      void syncFromServer(token);
    }
  }, [token, syncFromServer]);

  useEffect(() => {
    if (!token) return;
    void getWishlistShareLink(token)
      .then(setShareState)
      .catch(() => setShareState({ token: null, enabled: false }));
  }, [token]);

  useEffect(() => {
    const ids = [...productIds];
    if (!ids.length) {
      setLoading(false);
      setProducts([]);
      return;
    }

    setLoading(true);
    void Promise.all(ids.map((id) => getProduct(id).catch(() => null)))
      .then((results) => setProducts(results.filter(Boolean) as ProductDto[]))
      .finally(() => setLoading(false));
  }, [productIds]);

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

  const shareUrl = useMemo(() => {
    if (!shareState.enabled || !shareState.token) return '';
    const origin = typeof window !== 'undefined' ? window.location.origin : '';
    return `${origin}/${locale}/wishlist/shared/${shareState.token}`;
  }, [shareState.enabled, shareState.token, locale]);

  const onEnableShare = async () => {
    if (!token || shareLoading) return;
    setShareLoading(true);
    setShareMessage(null);
    try {
      const next = await enableWishlistShareLink(token);
      setShareState(next);
      setShareMessage(locale === 'es' ? 'Link de favoritos activado.' : 'Wishlist link enabled.');
    } catch {
      setShareMessage(locale === 'es' ? 'No pudimos activar el link compartido.' : 'Could not enable shared link.');
    } finally {
      setShareLoading(false);
    }
  };

  const onDisableShare = async () => {
    if (!token || shareLoading) return;
    setShareLoading(true);
    setShareMessage(null);
    try {
      await disableWishlistShareLink(token);
      setShareState((prev) => ({ ...prev, enabled: false }));
      setShareMessage(locale === 'es' ? 'Link compartido desactivado.' : 'Shared link disabled.');
    } catch {
      setShareMessage(locale === 'es' ? 'No pudimos desactivar el link.' : 'Could not disable link.');
    } finally {
      setShareLoading(false);
    }
  };

  const onCopyShare = async () => {
    if (!shareUrl) return;
    try {
      await navigator.clipboard.writeText(shareUrl);
      setShareMessage(locale === 'es' ? 'Link copiado al portapapeles.' : 'Link copied to clipboard.');
    } catch {
      setShareMessage(locale === 'es' ? 'No pudimos copiar el link.' : 'Could not copy the link.');
    }
  };

  const sharePanel = token ? (
    <section className="border border-[#C9BCA9]/50 bg-[#F6EFE6] dark:bg-[#181214] dark:border-[#3F2A2F] p-4 sm:p-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-[10px] tracking-[0.28em] uppercase text-[#8E4F58] mb-1">
            {locale === 'es' ? 'Wishlist compartible' : 'Shareable wishlist'}
          </p>
          <p className="text-sm text-[#3A3A3A] dark:text-[#D6C8B5]">
            {locale === 'es'
              ? 'Genera un link publico para compartir tus favoritos.'
              : 'Generate a public link to share your wishlist.'}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {!shareState.enabled ? (
            <button
              type="button"
              onClick={onEnableShare}
              disabled={shareLoading}
              className="px-3 py-2 text-[11px] tracking-widest uppercase bg-[#B76E79] text-[#F8F4EF] hover:bg-[#8E4F58] transition-colors disabled:opacity-60"
            >
              {locale === 'es' ? 'Activar link' : 'Enable link'}
            </button>
          ) : (
            <>
              <button
                type="button"
                onClick={onCopyShare}
                className="px-3 py-2 text-[11px] tracking-widest uppercase border border-[#B76E79]/50 text-[#8E4F58] dark:text-[#E4B8BF] hover:bg-[#B76E79]/10 transition-colors"
              >
                {locale === 'es' ? 'Copiar link' : 'Copy link'}
              </button>
              <button
                type="button"
                onClick={onDisableShare}
                disabled={shareLoading}
                className="px-3 py-2 text-[11px] tracking-widest uppercase border border-[#8E4F58]/35 text-[#8E4F58] dark:text-[#D6C8B5] hover:bg-[#8E4F58]/10 transition-colors disabled:opacity-60"
              >
                {locale === 'es' ? 'Desactivar' : 'Disable'}
              </button>
            </>
          )}
        </div>
      </div>
      {shareState.enabled && shareUrl ? (
        <div className="mt-3 p-2.5 border border-dashed border-[#C9BCA9] dark:border-[#4A3238] text-xs break-all text-[#5B4A3B] dark:text-[#D6C8B5]">
          {shareUrl}
        </div>
      ) : null}
      {shareMessage ? (
        <p className="mt-3 text-xs text-[#8E4F58] dark:text-[#E4B8BF]">{shareMessage}</p>
      ) : null}
    </section>
  ) : null;

  if (loading) {
    return (
      <div className="space-y-6">
        {sharePanel}
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="aspect-[3/4] bg-[#DDCCB8] animate-pulse" />
          ))}
        </div>
      </div>
    );
  }

  if (!products.length) {
    return (
      <div className="space-y-6">
        {sharePanel}
        <div className="text-center py-24">
          <img src="/ornaments/seal-pe.svg" alt="" className="w-24 h-24 mx-auto mb-6 opacity-20" />
          <p className="font-['Cormorant_Garamond',serif] text-2xl text-[#3A3A3A] mb-4">
            {locale === 'es' ? 'Tu lista de favoritos esta vacia' : 'Your wishlist is empty'}
          </p>
          <a
            href={`/${locale}/products`}
            className="inline-flex items-center gap-2 bg-[#B76E79] text-[#F8F4EF] px-6 py-3 text-sm tracking-widest uppercase hover:bg-[#8E4F58] transition-colors"
          >
            {locale === 'es' ? 'Explorar productos' : 'Explore products'}
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {sharePanel}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
        {products.map((p) => (
          <div key={p.id} className="pe-wishlist-card group relative">
            <button
              onClick={() => void remove(p.id, token)}
              className="pe-wishlist-remove absolute top-3 right-3 z-10 w-8 h-8 flex items-center justify-center transition-colors"
              aria-label="Quitar de favoritos"
            >
              <X size={14} strokeWidth={1.5} className="text-[#1A1A1A]" />
            </button>

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
              {locale === 'es' ? 'Mover al carrito' : 'Move to cart'}
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
