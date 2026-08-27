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

const SKELETON_TILES = ['sk-1', 'sk-2', 'sk-3', 'sk-4'];

interface Props {
  readonly locale?: string;
  readonly token?: string;
}

interface SharePanelProps {
  readonly locale: string;
  readonly shareState: WishlistShareLinkDto;
  readonly shareLoading: boolean;
  readonly shareUrl: string;
  readonly shareMessage: string | null;
  readonly onEnable: () => void;
  readonly onDisable: () => void;
  readonly onCopy: () => void;
}

function SharePanel({ locale, shareState, shareLoading, shareUrl, shareMessage, onEnable, onDisable, onCopy }: SharePanelProps) {
  return (
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
              onClick={onEnable}
              disabled={shareLoading}
              className="px-3 py-2 text-[11px] tracking-widest uppercase bg-[#B76E79] text-[#F8F4EF] hover:bg-[#8E4F58] transition-colors disabled:opacity-60"
            >
              {locale === 'es' ? 'Activar link' : 'Enable link'}
            </button>
          ) : (
            <>
              <button
                type="button"
                onClick={onCopy}
                className="px-3 py-2 text-[11px] tracking-widest uppercase border border-[#B76E79]/50 text-[#8E4F58] dark:text-[#E4B8BF] hover:bg-[#B76E79]/10 transition-colors"
              >
                {locale === 'es' ? 'Copiar link' : 'Copy link'}
              </button>
              <button
                type="button"
                onClick={onDisable}
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
  );
}

interface WishlistCardProps {
  readonly product: ProductDto;
  readonly locale: string;
  readonly token: string | undefined;
  readonly onRemove: (id: string, token: string | undefined) => void;
  readonly onAddToCart: (product: ProductDto) => void;
}

function WishlistCard({ product: p, locale, token, onRemove, onAddToCart }: WishlistCardProps) {
  const discounted = !!p.listPrice && p.listPrice.currency === p.price.currency && p.listPrice.amount > p.price.amount;
  const format = (amount: number, currency?: string) =>
    new Intl.NumberFormat('es-CL', { style: 'currency', currency: currency ?? 'CLP', maximumFractionDigits: 0 }).format(amount);

  return (
    <div className="pe-wishlist-card group relative">
      <button
        type="button"
        onClick={() => onRemove(p.id, token)}
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
        {discounted && (
          <p className="pe-wishlist-price font-sans text-xs text-[#3A3A3A]/45 line-through">
            {format(p.listPrice!.amount, p.listPrice!.currency)}
          </p>
        )}
        <p className="pe-wishlist-price font-['Cormorant_Garamond',serif]">{format(p.price.amount, p.price.currency)}</p>
      </a>

      <button
        type="button"
        onClick={() => onAddToCart(p)}
        className="pe-wishlist-cart-btn mt-3 w-full flex items-center justify-center gap-2 py-2.5 text-xs tracking-widest uppercase transition-colors"
      >
        <ShoppingBag size={14} strokeWidth={1.25} />
        {locale === 'es' ? 'Mover al carrito' : 'Move to cart'}
      </button>
    </div>
  );
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
    <SharePanel
      locale={locale}
      shareState={shareState}
      shareLoading={shareLoading}
      shareUrl={shareUrl}
      shareMessage={shareMessage}
      onEnable={onEnableShare}
      onDisable={onDisableShare}
      onCopy={onCopyShare}
    />
  ) : null;

  if (loading) {
    return (
      <div className="space-y-6">
        {sharePanel}
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
          {SKELETON_TILES.map((key) => (
            <div key={key} className="aspect-[3/4] bg-[#DDCCB8] animate-pulse" />
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
          <WishlistCard
            key={p.id}
            product={p}
            locale={locale}
            token={token}
            onRemove={(id, tok) => void remove(id, tok)}
            onAddToCart={(product) => addToCart({
              id: product.id,
              name: product.name,
              brand: product.brand,
              price: product.price,
              imageUrl: product.imageUrl,
              condition: product.condition,
            })}
          />
        ))}
      </div>
    </div>
  );
}
