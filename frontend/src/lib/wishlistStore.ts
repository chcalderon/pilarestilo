import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { addToWishlist, removeFromWishlist, getWishlist } from './api';

interface WishlistState {
  productIds: Set<string>;
  add: (productId: string, token?: string) => Promise<void>;
  remove: (productId: string, token?: string) => Promise<void>;
  toggle: (productId: string, token?: string) => Promise<void>;
  has: (productId: string) => boolean;
  syncFromServer: (token: string) => Promise<void>;
  count: () => number;
}

export const useWishlistStore = create<WishlistState>()(
  persist(
    (set, get) => ({
      productIds: new Set<string>(),

      add: async (productId, token) => {
        set(s => ({ productIds: new Set([...s.productIds, productId]) }));
        if (token) {
          try { await addToWishlist(productId, token); } catch { /* optimistic — keep local */ }
        }
      },

      remove: async (productId, token) => {
        set(s => {
          const next = new Set(s.productIds);
          next.delete(productId);
          return { productIds: next };
        });
        if (token) {
          try { await removeFromWishlist(productId, token); } catch { /* optimistic */ }
        }
      },

      toggle: async (productId, token) => {
        if (get().has(productId)) {
          await get().remove(productId, token);
        } else {
          await get().add(productId, token);
        }
      },

      has: (productId) => get().productIds.has(productId),

      syncFromServer: async (token) => {
        try {
          const dto = await getWishlist(token);
          const serverIds = new Set(dto.productIds);
          // Union: keep local items + server items
          set(s => ({ productIds: new Set([...s.productIds, ...serverIds]) }));
        } catch { /* ignore */ }
      },

      count: () => get().productIds.size,
    }),
    {
      name: 'pe-wishlist',
      storage: {
        getItem: (key) => {
          const v = localStorage.getItem(key);
          if (!v) return null;
          const parsed = JSON.parse(v);
          if (parsed?.state?.productIds) {
            parsed.state.productIds = new Set(parsed.state.productIds);
          }
          return parsed;
        },
        setItem: (key, value) => {
          const toStore = {
            ...value,
            state: {
              ...value.state,
              productIds: [...(value.state.productIds as Set<string>)],
            },
          };
          localStorage.setItem(key, JSON.stringify(toStore));
        },
        removeItem: (key) => localStorage.removeItem(key),
      },
    }
  )
);
