import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { getProduct } from './api';

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

type StockVerifyResult =
  | { ok: true }
  | { ok: false; availableQty: number; productName: string };

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
        return { ok: false, availableQty: 0, productName: product.name };
      }
      if (requestedQty <= match.stockAvailable) {
        return { ok: true };
      }
      return { ok: false, availableQty: match.stockAvailable, productName: product.name };
    }

    if (requestedQty <= product.stock) {
      return { ok: true };
    }
    return { ok: false, availableQty: product.stock, productName: product.name };
  } catch {
    return { ok: true };
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
          const existing = state.items.find((i) => i.id === normalized.id);
          if (existing) {
            return {
              items: state.items.map((i) =>
                i.id === normalized.id ? { ...i, quantity: i.quantity + 1 } : i
              ),
            };
          }
          return { items: [...state.items, normalized] };
        });
      },

      removeItem: (id) => {
        set((state) => ({ items: state.items.filter((i) => i.id !== id) }));
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
