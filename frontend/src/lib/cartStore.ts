import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { getProduct } from './api';
import { track } from './analytics';

/** Shared shape for the add_to_cart / remove_from_cart funnel events. */
function itemEventProps(item: CartItem, quantity: number) {
  return {
    line_id: item.id,
    product_id: item.productId,
    name: item.name,
    brand: item.brand,
    price: item.price.amount,
    currency: item.price.currency,
    condition: item.condition,
    variant_color: item.variantColor,
    variant_size: item.variantSize,
    quantity,
  };
}

const UUID_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function extractBaseProductId(id: string): string {
  return id.split('::')[0]?.trim() ?? '';
}

function isUuid(value: string): boolean {
  return UUID_REGEX.test(value);
}

function normalizeString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value.trim() : fallback;
}

function normalizeAmount(value: unknown): number {
  const parsed = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) return 0;
  return parsed;
}

function normalizeQuantity(value: unknown): number {
  const parsed = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) return 1;
  return Math.floor(parsed);
}

function normalizeCondition(value: unknown): 'NEW' | 'USED' {
  return value === 'USED' ? 'USED' : 'NEW';
}

function normalizeCurrency(value: unknown): string {
  const raw = normalizeString(value, 'CLP');
  return raw || 'CLP';
}

export interface CartItem {
  id: string;
  productId?: string;
  name: string;
  brand: string;
  price: { amount: number; currency: string };
  imageUrl: string;
  condition: 'NEW' | 'USED';
  variantLabel?: string;
  variantColor?: string;
  variantSize?: string;
  quantity: number;
}

type PersistedCartItemLike = Partial<CartItem> & {
  priceAmount?: unknown;
  priceCurrency?: unknown;
};

function normalizePersistedItem(raw: unknown): CartItem | null {
  if (!raw || typeof raw !== 'object') return null;
  const item = raw as PersistedCartItemLike;
  const id = normalizeString(item.id);
  if (!id) return null;
  const productId = normalizeString(item.productId) || extractBaseProductId(id);
  if (!productId || !isUuid(productId)) return null;

  const amount = normalizeAmount(item.price?.amount ?? item.priceAmount);
  const currency = normalizeCurrency(item.price?.currency ?? item.priceCurrency);

  return {
    id,
    productId,
    name: normalizeString(item.name, 'Producto'),
    brand: normalizeString(item.brand),
    price: { amount, currency },
    imageUrl: normalizeString(item.imageUrl),
    condition: normalizeCondition(item.condition),
    variantLabel: normalizeString(item.variantLabel) || undefined,
    variantColor: normalizeString(item.variantColor) || undefined,
    variantSize: normalizeString(item.variantSize) || undefined,
    quantity: normalizeQuantity(item.quantity),
  };
}

function sanitizePersistedItems(items: unknown): CartItem[] {
  if (!Array.isArray(items)) return [];
  return items
    .map((raw) => normalizePersistedItem(raw))
    .filter((item): item is CartItem => item !== null);
}

interface CartState {
  items: CartItem[];
  addItem: (item: Omit<CartItem, 'quantity'>) => void;
  removeItem: (id: string) => void;
  updateQuantity: (id: string, quantity: number) => void;
  clearCart: () => void;
  getTotalItems: () => number;
  getSubtotal: () => number;
}

/**
 * `ok: true` with `verified: false` means the check could not be performed, not that
 * stock exists. Both are non-blocking on purpose — the backend reservation is the
 * authority, and refusing a purchase over a transient network error is the worse
 * failure — but callers must not present an unverified result as a confirmation.
 */
export type StockVerifyResult =
  | { ok: true; verified: true }
  | { ok: true; verified: false }
  | { ok: false; reason: 'INSUFFICIENT'; availableQty: number; productName: string }
  /**
   * The line names a product that sells by variant but carries no colour or size. It cannot be
   * ordered at all — inventory-service refuses the reservation outright — even though the
   * product itself is in stock. Cart lines like this come from the product grid, which used to
   * offer a plain add button for variant products.
   */
  | { ok: false; reason: 'NEEDS_VARIANT'; productName: string };

export async function verifyStockForItem(
  productId: string,
  variant: { color?: string; size?: string } | null,
  requestedQty: number
): Promise<StockVerifyResult> {
  try {
    const product = await getProduct(productId);
    const variants = product.variants ?? [];

    if (variants.length > 0 && variant) {
      const match = variants.find(
        (v) =>
          (!variant.color || v.color === variant.color) &&
          (!variant.size || v.size === variant.size)
      );
      if (!match) {
        return { ok: false, reason: 'INSUFFICIENT', availableQty: 0, productName: product.name };
      }
      if (requestedQty <= match.stockAvailable) {
        return { ok: true, verified: true };
      }
      return {
        ok: false,
        reason: 'INSUFFICIENT',
        availableQty: match.stockAvailable,
        productName: product.name,
      };
    }

    /*
     * One variant is not a choice. Twelve of the seventeen products in this catalogue are a
     * single Base/UNICO row, so a line naming no variant is unambiguous — it can only mean that
     * one. Resolve it rather than asking the customer to pick from a list of one.
     */
    if (variants.length === 1) {
      const only = variants[0];
      if (requestedQty <= only.stockAvailable) {
        return { ok: true, verified: true };
      }
      return {
        ok: false,
        reason: 'INSUFFICIENT',
        availableQty: only.stockAvailable,
        productName: product.name,
      };
    }

    /*
     * Several variants and none named. Falling back to products.stock here is what let such a
     * line pass: the aggregate counts every variant, so it looked available and was then refused
     * by inventory-service, which requires colour+size whenever a product has variants. The
     * customer only found out at the pay button.
     */
    if (variants.length > 1) {
      return { ok: false, reason: 'NEEDS_VARIANT', productName: product.name };
    }

    if (requestedQty <= product.stock) {
      return { ok: true, verified: true };
    }
    return {
      ok: false,
      reason: 'INSUFFICIENT',
      availableQty: product.stock,
      productName: product.name,
    };
  } catch {
    return { ok: true, verified: false };
  }
}

export const useCartStore = create<CartState>()(
  persist(
    (set, get) => ({
      items: [],

      addItem: (item) => {
        const normalized = normalizePersistedItem({ ...item, quantity: 1 });
        if (!normalized) return;
        set((state) => {
          const existing = state.items.some((i) => i.id === normalized.id);
          if (existing) {
            return {
              items: state.items.map((i) =>
                i.id === normalized.id ? { ...i, quantity: i.quantity + 1 } : i
              ),
            };
          }
          return { items: [...state.items, normalized] };
        });
        // Every add path (product page, variant selector, cart-page undo) funnels through here.
        track('add_to_cart', itemEventProps(normalized, 1));
      },

      removeItem: (id) => {
        const removed = get().items.find((i) => i.id === id);
        set((state) => ({ items: state.items.filter((i) => i.id !== id) }));
        if (removed) track('remove_from_cart', itemEventProps(removed, removed.quantity));
      },

      updateQuantity: (id, quantity) => {
        if (quantity <= 0) {
          get().removeItem(id);
          return;
        }
        set((state) => ({
          items: state.items.map((i) => (i.id === id ? { ...i, quantity } : i)),
        }));
      },

      clearCart: () => set({ items: [] }),

      getTotalItems: () => get().items.reduce((sum, i) => sum + i.quantity, 0),

      getSubtotal: () =>
        get().items.reduce((sum, i) => sum + i.price.amount * i.quantity, 0),
    }),
    {
      name: 'pe-cart',
      version: 3,
      migrate: (persistedState) => {
        if (!persistedState || typeof persistedState !== 'object') {
          return { items: [] };
        }
        const state = persistedState as { items?: unknown };
        const items = sanitizePersistedItems(state.items);
        return { ...state, items };
      },
      merge: (persistedState, currentState) => {
        const persisted = (persistedState as { items?: unknown }) ?? {};
        return {
          ...currentState,
          ...persisted,
          items: sanitizePersistedItems(persisted.items),
        };
      },
    }
  )
);
